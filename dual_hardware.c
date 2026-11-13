/* Experimental MiniTiouner Pro dual receiver. GPL-3.0-or-later.
 * Only the controller thread may call start/retune/report: the NIM's I2C
 * repeater and STV6120 register shadows are shared by the two demodulators.
 */
#include <string.h>
#include <unistd.h>
#include <libusb-1.0/libusb.h>
#include "dual_hardware.h"
#include "errors.h"
#include "ftdi.h"
#include "ftdi_usb.h"
#include "nim.h"
#include "stv0910.h"
#include "stv0910_utils.h"
#include "stv0910_regs.h"
#include "stv6120.h"
#include "stv6120_regs.h"
#include "stv6120_utils.h"

/* Resolve explicit serials before opening/initialising either interface.
 * Never rely on USB enumeration order or TS1/TS2 product strings. */
static int find_serial(const char *serial, uint8_t *bus, uint8_t *address)
{
    libusb_context *ctx = NULL;
    libusb_device **list = NULL;
    int matches = 0;
    if (libusb_init(&ctx) < 0) return -1;
    ssize_t count = libusb_get_device_list(ctx, &list);
    if (count < 0) { libusb_exit(ctx); return -1; }
    for (ssize_t i = 0; i < count; i++) {
        struct libusb_device_descriptor desc;
        libusb_device_handle *handle = NULL;
        if (libusb_get_device_descriptor(list[i], &desc) < 0 ||
            desc.idVendor != 0x0403 || desc.idProduct != 0x6010 ||
            !desc.iSerialNumber) continue;
        if (libusb_open(list[i], &handle) < 0) continue;
        unsigned char text[256];
        int len = libusb_get_string_descriptor_ascii(handle, desc.iSerialNumber,
                                                     text, sizeof(text) - 1);
        if (len > 0) {
            text[len] = '\0';
            if (!strcmp((char *)text, serial)) {
                *bus = libusb_get_bus_number(list[i]);
                *address = libusb_get_device_address(list[i]);
                matches++;
            }
        }
        libusb_close(handle);
    }
    libusb_free_device_list(list, 1);
    libusb_exit(ctx);
    if (matches != 1) {
        fprintf(stderr, "Dual: serial %s: %d accessible devices (need exactly one).\n",
                serial, matches);
        return -1;
    }
    return 0;
}

int dual_hardware_open(const char *master_serial, const char *aux_serial)
{
    uint8_t bus[2], address[2];
    if (!strcmp(master_serial, aux_serial) ||
        find_serial(master_serial, &bus[0], &address[0]) ||
        find_serial(aux_serial, &bus[1], &address[1])) return -1;
    if (bus[0] == bus[1] && address[0] == address[1]) return -1;
    printf("Dual: master=%s USB %u,%u; aux=%s USB %u,%u\n",
           master_serial, bus[0], address[0], aux_serial, bus[1], address[1]);
    longmynd_channel = 0;
    uint8_t err = ftdi_init(bus[0], address[0]);
    if (!err) {
        longmynd_channel = 1;
        /* AUX channel A is not the NIM control bus. Do not initialise it or
         * drive its GPIO/reset pins. Channel B must already be EEPROM 245 FIFO. */
        err = ftdi_usb_init_ts(bus[1], address[1], 0x0403, 0x6010);
        if (!err) err = ftdi_usb_prepare_aux_ts();
    }
    longmynd_channel = 0;
    return err ? -1 : 0;
}

static uint32_t frequency(const longmynd_config_t *c) { return c->freq_requested[c->freq_index]; }
static uint32_t symbolrate(const longmynd_config_t *c) { return c->sr_requested[c->sr_index]; }

static uint8_t select_inputs(const longmynd_config_t c[2])
{
    /* Tuner 1 -> demod P2 (original TS), tuner 2 -> P1 (AUX TS).
     * RFB = top F connector, RFC = bottom. Both tuners may use the same input. */
    return stv6120_write_reg(STV6120_CTRL9, STV6120_CTRL9_RESERVED |
        ((c[0].port_swap ? STV6120_CTRL9_RFSEL_RFC_IN : STV6120_CTRL9_RFSEL_RFB_IN)
            << STV6120_CTRL9_RFSEL_1_SHIFT) |
        ((c[1].port_swap ? STV6120_CTRL9_RFSEL_RFC_IN : STV6120_CTRL9_RFSEL_RFB_IN)
            << STV6120_CTRL9_RFSEL_2_SHIFT));
}

uint8_t dual_hardware_start(const longmynd_config_t c[2], bool lna_present[2])
{
    uint8_t err = nim_init();
    if (!err) err = stv0910_init(symbolrate(&c[0]), symbolrate(&c[1]),
                                c[0].halfscan_ratio, c[1].halfscan_ratio);
    /* Match OpenTuner's final TSSTATUS2 writes after GENCFG / global reset.
     * The original table sets these before GENCFG, whereas the reference
     * reapplies both at the end. Hardware verification is still required. */
    if (!err) err = stv0910_write_reg(RSTV0910_TSGENERAL, 0x00);
    if (!err) err = stv0910_write_reg(RSTV0910_P1_TSSTATUS2, 0x02);
    if (!err) err = stv0910_write_reg(RSTV0910_P2_TSSTATUS2, 0x02);
    if (!err) err = stv6120_init(frequency(&c[0]), frequency(&c[1]), false);
    if (!err) err = select_inputs(c);
    /* Both front-end LNAs stay enabled; retuning never changes peer power. */
    if (!err) err = stvvglna_init(NIM_INPUT_TOP, STVVGLNA_ON, &lna_present[0]);
    if (!err) err = stvvglna_init(NIM_INPUT_BOTTOM, STVVGLNA_ON, &lna_present[1]);
    if (!err) err = ftdi_set_polarisation_supply(false, false);
    if (!err) err = stv0910_start_scan(STV0910_DEMOD_TOP);
    if (!err) err = stv0910_start_scan(STV0910_DEMOD_BOTTOM);
    return err;
}

uint8_t dual_hardware_retune(unsigned channel, const longmynd_config_t c[2])
{
    uint8_t demod = channel ? STV0910_DEMOD_BOTTOM : STV0910_DEMOD_TOP;
    uint16_t stop_reg = channel ? RSTV0910_P1_DMDISTATE : RSTV0910_P2_DMDISTATE;
    uint8_t err = stv0910_write_reg(stop_reg, 0x1c);
    if (!err) err = select_inputs(c);
    if (!err) err = stv6120_set_freq(channel ? TUNER_2 : TUNER_1, frequency(&c[channel]));
    if (!err) err = stv0910_setup_carrier_loop(demod, symbolrate(&c[channel]) * c[channel].halfscan_ratio);
    if (!err) err = stv0910_setup_timing_loop(demod, symbolrate(&c[channel]));
    if (!err) err = stv0910_start_scan(demod);
    return err;
}

uint8_t dual_hardware_report(unsigned channel, longmynd_status_t *status)
{
    uint8_t demod = channel ? STV0910_DEMOD_BOTTOM : STV0910_DEMOD_TOP;
    uint8_t err = stv0910_read_scan_state(demod, &status->demod_state);
    if (err) return err;
    switch (status->demod_state) {
        case DEMOD_HUNTING: status->state = STATE_DEMOD_HUNTING; break;
        case DEMOD_FOUND_HEADER: status->state = STATE_DEMOD_FOUND_HEADER; break;
        case DEMOD_S: status->state = STATE_DEMOD_S; break;
        case DEMOD_S2: status->state = STATE_DEMOD_S2; break;
        default: return ERROR_BAD_DEMOD_HUNT_STATE;
    }
    /* Match the Linux driver's per-demod stream-merger reset on first lock.
     * Never reset the peer TS path when this channel changes state. */
    static bool ts_was_locked[2];
    bool locked = status->state == STATE_DEMOD_S || status->state == STATE_DEMOD_S2;
    if (locked && !ts_was_locked[channel]) {
        uint16_t reg = channel ? RSTV0910_P1_TSCFGH : RSTV0910_P2_TSCFGH;
        uint8_t cfg;
        err = stv0910_read_reg(reg, &cfg);
        if (!err) err = stv0910_write_reg(reg, cfg & ~1u);
        if (!err) usleep(4000);
        if (!err) err = stv0910_write_reg(reg, cfg | 1u);
        if (!err) err = stv0910_write_reg(reg, cfg & ~1u);
        if (err) return err;
        printf("Dual-TS-reset: channel %u\n", channel + 1);
    }
    ts_was_locked[channel] = locked;
    /* Read back the actual hardware registers once each demod locks. */
    static bool reported[2];
    if (!reported[channel] && (status->state == STATE_DEMOD_S || status->state == STATE_DEMOD_S2)) {
        const uint16_t regs[] = { RSTV0910_TSGENERAL, RSTV0910_GENCFG,
            RSTV0910_P1_TSCFGH, RSTV0910_P2_TSCFGH,
            RSTV0910_P1_TSSTATUS, RSTV0910_P2_TSSTATUS,
            RSTV0910_P1_TSSTATUS2, RSTV0910_P2_TSSTATUS2,
            RSTV0910_P1_TSBITRATE1, RSTV0910_P1_TSBITRATE0,
            RSTV0910_P2_TSBITRATE1, RSTV0910_P2_TSBITRATE0 };
        printf("Dual-Regs: lock channel %u", channel + 1);
        for (unsigned i = 0; i < sizeof(regs)/sizeof(regs[0]); i++) {
            uint8_t value = 0;
            err = stv0910_read_reg(regs[i], &value);
            if (err) { printf(" read error\n"); return err; }
            printf(" %04x=%02x", regs[i], value);
        }
        printf("\n");
        reported[channel] = true;
    }
    return do_report_demod(status, demod);
}

void dual_hardware_close(void) { ftdi_usb_close_all(); }
