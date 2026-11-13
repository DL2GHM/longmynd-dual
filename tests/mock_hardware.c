/* Test-only hardware simulator. Never linked into the real receiver. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include "dual_hardware.h"
#include "errors.h"

int dual_hardware_open(const char *master, const char *aux) {
    printf("MOCK_OPEN %s %s\n", master, aux); fflush(stdout); return 0;
}
uint8_t dual_hardware_start(const longmynd_config_t c[2], bool lna[2]) {
    (void)c; lna[0] = lna[1] = true; return 0;
}
uint8_t dual_hardware_retune(unsigned ch, const longmynd_config_t c[2]) {
    printf("MOCK_RETUNE %u %u %u\n", ch, c[ch].freq_requested[0], c[ch].sr_requested[0]);
    fflush(stdout); return 0;
}
uint8_t dual_hardware_report(unsigned ch, longmynd_status_t *s) {
    if (getenv("MOCK_SLOW_REPORT")) usleep(200000);
    s->state = STATE_DEMOD_S2; s->demod_state = 2;
    s->symbolrate = s->symbolrate_requested * 1000;
    s->frequency_offset = 0; s->modulation_error_rate = 100 + ch;
    s->modcod = 8; return 0;
}
void dual_hardware_close(void) { puts("MOCK_CLOSE"); fflush(stdout); }

static uint32_t crc(const uint8_t *s, size_t n) {
    uint32_t c = 0xffffffff;
    while (n--) {
        c ^= (uint32_t)*s++ << 24;
        for (unsigned bit = 0; bit < 8; bit++) c = c & 0x80000000 ? (c << 1) ^ 0x04c11db7 : c << 1;
    }
    return c;
}
static void packet(uint8_t out[188], unsigned ch, unsigned phase) {
    memset(out, 0xff, 188);
    out[0] = 0x47; out[1] = 0x40; out[2] = 0x11; out[3] = 0x10; out[4] = 0;
    uint8_t *s = out + 5;
    size_t n;
    if (phase == 0) {
        /* Valid SDT with different service/provider names per stream. */
        uint8_t body[] = {0x42,0xf0,0,0,1,0xc1,0,0,0,1,0xff,0,1,0,0x80,0,0x48,0,1,4};
        memcpy(s, body, sizeof(body));
        memcpy(s+20, ch ? "PRO1" : "PRO0", 4);
        s[24] = 4; memcpy(s+25, ch ? "AUX1" : "MAIN", 4);
        n = 29; s[2] = n + 1; s[15] = 13; s[17] = 11;
    } else if (phase == 1) {
        out[1] = 0x41 + ch; out[2] = 0;
        uint8_t body[] = {2,0xb0,18,0,1,0xc1,0,0,0xe1,1,0xf0,0,27,0xe1,1,0xf0,0};
        memcpy(s, body, sizeof(body)); n = sizeof(body);
        s[8] += ch; s[13] += ch;
    } else {
        out[1] = 0x1f; out[2] = 0xff; return;
    }
    uint32_t sum = crc(s, n);
    for (int i=0;i<4;i++) s[n+i] = sum >> (24 - 8*i);
}
uint8_t __wrap_ftdi_usb_ts_read(uint8_t *buffer, uint16_t *len, uint32_t size) {
    static _Thread_local unsigned phase;
    (void)size; usleep(20000);
    if (longmynd_channel == 1 && getenv("MOCK_STALL_SECOND")) { *len = 0; return 0; }
    buffer[0] = 0x01; buffer[1] = 0x60;
    packet(buffer+2, longmynd_channel, phase++ % 3);
    *len = 190; return 0;
}
