/* Experimental MiniTiouner Pro dual receiver. GPL-3.0-or-later. */
#include <arpa/inet.h>
#include <errno.h>
#include <math.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include "main.h"
#include "errors.h"
#include "dual_hardware.h"
#include "ts.h"
#include "udp.h"
#include "web/web.h"

_Thread_local unsigned longmynd_channel;
bool longmynd_dual_mode;
static _Atomic uint8_t dual_error;
static void fail(uint8_t err) { if (err) { uint8_t expected = ERROR_NONE; atomic_compare_exchange_strong(&dual_error, &expected, err); } }
static void dual_signal(int sig) { (void)sig; dual_error = ERROR_SIGNAL_TERMINATE; }

static void usage(void)
{
    puts("Experimental MiniTiouner Pro dual mode (two FT2232H modules):\n"
         "  ./longmynd --dual MASTER_SERIAL AUX_SERIAL CH1_OPTIONS FREQ SR --next CH2_OPTIONS FREQ SR\n"
         "  Both channels require -W WEB_PORT -i TS_IP TS_PORT -I STATUS_IP STATUS_PORT.\n"
         "  Optional per channel: -w (bottom RF input), -r TIMEOUT_MS, -S SCAN_RATIO.\n"
         "  FREQ in kHz, SR in ksymbols/s. No lists/FIFOs/LNB voltage control in this prototype.\n"
         "  Example for DL2GHM (both channels use TOP RF input):\n"
         "  ./longmynd --dual MASTER_SERIAL AUX_SERIAL \\\n"
         "    -W 8080 -i 230.0.0.12 4003 -I 230.0.0.12 4002 741538 1500 --next \\\n"
         "    -W 8081 -i 230.0.0.13 4003 -I 230.0.0.13 4002 741538 1500");
}

static bool integer(const char *s, long min, long max)
{
    char *end;
    errno = 0;
    if (!s || !*s) return false;
    long v = strtol(s, &end, 10);
    return !errno && !*end && v >= min && v <= max;
}

/* Check bounds before calling the legacy parser, which assumes complete argv. */
static bool valid_channel_args(int argc, char **argv)
{
    bool web = false, ts = false, status = false;
    int i = 1;
    for (; i < argc && argv[i][0] == '-'; i++) {
        const char *option = argv[i];
        if (!strcmp(option, "-w")) continue;
        if (!strcmp(option, "-i") || !strcmp(option, "-I")) {
            struct in_addr address;
            if (i + 2 >= argc || inet_pton(AF_INET, argv[i+1], &address) != 1 ||
                !integer(argv[i+2], 1, 65535)) return false;
            if (option[1] == 'i') { if (ts) return false; ts = true; }
            else { if (status) return false; status = true; }
            i += 2;
        } else if (!strcmp(option, "-W")) {
            if (web || i + 1 >= argc || !integer(argv[++i], 1, 65535)) return false;
            web = true;
        } else if (!strcmp(option, "-r")) {
            if (i + 1 >= argc || !integer(argv[++i], -1, 2147483647L)) return false;
            long timeout = strtol(argv[i], NULL, 10);
            if (timeout != -1 && timeout < 1000) return false;
        } else if (!strcmp(option, "-S")) {
            char *end;
            if (i + 1 >= argc) return false;
            errno = 0;
            float ratio = strtof(argv[++i], &end);
            if (errno || !*argv[i] || *end || !isfinite(ratio) || ratio <= 0 || ratio > 100) return false;
        } else return false;
    }
    return web && ts && status && i + 2 == argc &&
        integer(argv[i], 144000, 2450000) && integer(argv[i+1], 33, 27500);
}

/* Copy only configuration data, never mutexes or concurrently changed TS flags. */
static bool snapshot(unsigned channel, longmynd_config_t *copy)
{
    longmynd_config_t *c = channel_config(channel);
    pthread_mutex_lock(&c->mutex);
    bool changed = atomic_exchange(&c->new, false);
    copy->port_swap = c->port_swap;
    copy->halfscan_ratio = c->halfscan_ratio;
    copy->freq_index = c->freq_index;
    copy->sr_index = c->sr_index;
    memcpy(copy->freq_requested, c->freq_requested, sizeof(copy->freq_requested));
    memcpy(copy->sr_requested, c->sr_requested, sizeof(copy->sr_requested));
    copy->ts_timeout = c->ts_timeout;
    pthread_mutex_unlock(&c->mutex);
    return changed;
}

static void reset_status(unsigned channel, const longmynd_config_t *c, const bool lna[2])
{
    longmynd_status_t *s = channel_status(channel);
    pthread_mutex_lock(&s->mutex);
    atomic_fetch_add(&channel_config(channel)->ts_generation, 1);
    s->state = STATE_DEMOD_HUNTING;
    s->rfport_index = c->port_swap ? 1 : 0;
    s->frequency_requested = c->freq_requested[c->freq_index];
    s->symbolrate_requested = c->sr_requested[c->sr_index];
    s->lna_ok = lna[s->rfport_index];
    s->last_ts_or_reinit_monotonic = monotonic_ms();
    s->service_name[0] = s->service_provider_name[0] = '\0';
    memset(s->ts_elementary_streams, 0, sizeof(s->ts_elementary_streams));
    s->ts_null_percentage = 100;
    pthread_mutex_unlock(&s->mutex);
    channel_config(channel)->ts_reset = true;
}

static bool same_output(const char *a, int ap, const char *b, int bp)
{ return ap == bp && !strcmp(a, b); }

int dual_main(int argc, char **argv)
{
    if (argc == 3 && !strcmp(argv[2], "--help")) { usage(); return 0; }
    int separator = 0;
    for (int i = 4; i < argc; i++) if (!strcmp(argv[i], "--next")) {
        if (separator) { usage(); return ERROR_ARGS_INPUT; }
        separator = i;
    }
    if (argc < 7 || !separator || !strcmp(argv[2], argv[3]) ||
        !*argv[2] || !*argv[3]) { usage(); return ERROR_ARGS_INPUT; }
    /* Reuse argv[3] and --next as harmless argv[0] placeholders. */
    int counts[2] = { separator - 3, argc - separator };
    char **args[2] = { argv + 3, argv + separator };
    for (unsigned ch = 0; ch < 2; ch++) {
        if (!valid_channel_args(counts[ch], args[ch])) { usage(); return ERROR_ARGS_INPUT; }
        longmynd_channel = ch;
        if (process_command_line(counts[ch], args[ch], channel_config(ch))) return ERROR_ARGS_INPUT;
    }
    longmynd_config_t *a = channel_config(0), *b = channel_config(1);
    if (a->web_port == b->web_port ||
        same_output(a->ts_ip_addr,a->ts_ip_port,b->ts_ip_addr,b->ts_ip_port) ||
        same_output(a->status_ip_addr,a->status_ip_port,b->status_ip_addr,b->status_ip_port) ||
        same_output(a->ts_ip_addr,a->ts_ip_port,b->status_ip_addr,b->status_ip_port) ||
        same_output(a->status_ip_addr,a->status_ip_port,b->ts_ip_addr,b->ts_ip_port)) {
        fputs("Dual: channels need distinct web ports and UDP destinations.\n", stderr);
        return ERROR_ARGS_INPUT;
    }
    longmynd_dual_mode = true;
    dual_error = ERROR_NONE;
    signal(SIGINT, dual_signal);
    signal(SIGTERM, dual_signal);
    signal(SIGPIPE, SIG_IGN);
    longmynd_channel = 0;
    if (dual_hardware_open(argv[2], argv[3])) { dual_hardware_close(); return ERROR_FTDI_USB_INIT; }
    longmynd_config_t applied[2] = {0};
    bool lna[2] = { false, false };
    for (unsigned ch = 0; ch < 2; ch++) snapshot(ch, &applied[ch]);
    fail(dual_hardware_start(applied, lna));
    pthread_t workers[2][3];
    bool started[2][3] = {{false}};
    thread_vars_t vars[2][3] = {{{0}}};
    void *(*functions[3])(void *) = { loop_ts_parse, loop_ts, loop_web };
    uint32_t last_count[2] = {0};
    for (unsigned ch = 0; ch < 2 && !dual_error; ch++) {
        longmynd_channel = ch;
        reset_status(ch, &applied[ch], lna);
        fail(udp_status_init(channel_config(ch)->status_ip_addr, channel_config(ch)->status_ip_port));
        for (unsigned w = 0; w < 3 && !dual_error; w++) {
            vars[ch][w].main_err_ptr = &dual_error;
            vars[ch][w].config = channel_config(ch);
            vars[ch][w].status = channel_status(ch);
            vars[ch][w].channel = ch;
            if (pthread_create(&workers[ch][w], NULL, functions[w], &vars[ch][w])) {
                dual_error = ERROR_THREAD_ERROR;
            } else started[ch][w] = true;
        }
    }
    /* Single I2C owner: all shared register transactions are serialised here. */
    while (!dual_error) {
        for (unsigned ch = 0; ch < 2 && !dual_error; ch++) {
            longmynd_channel = ch;
            bool changed = snapshot(ch, &applied[ch]);
            longmynd_status_t *s = channel_status(ch);
            uint32_t count = atomic_load(&s->ts_packet_count_nolock);
            uint64_t now = monotonic_ms();
            pthread_mutex_lock(&s->mutex);
            if (count != last_count[ch]) { s->last_ts_or_reinit_monotonic = now; last_count[ch] = count; }
            bool timeout = applied[ch].ts_timeout != -1 &&
                now - s->last_ts_or_reinit_monotonic > (uint64_t)applied[ch].ts_timeout;
            pthread_mutex_unlock(&s->mutex);
            if (changed || timeout) {
                printf("Dual: retune channel %u to %u kHz / %u ksymbols/s\n", ch + 1,
                    applied[ch].freq_requested[applied[ch].freq_index],
                    applied[ch].sr_requested[applied[ch].sr_index]);
                /* No global demod/tuner initialisation or GPIO reset here. */
                fail(dual_hardware_retune(ch, applied));
                if (dual_error) break;
                reset_status(ch, &applied[ch], lna);
            }
            pthread_mutex_lock(&s->mutex);
            fail(dual_hardware_report(ch, s));
            s->last_updated_monotonic = monotonic_ms();
            bool ready = true;
            if (!dual_error) fail(status_all_write(s, udp_status_write, udp_status_string_write, &ready));
            pthread_cond_signal(&s->signal);
            pthread_mutex_unlock(&s->mutex);
            for (unsigned w = 0; w < 3; w++) if (vars[ch][w].thread_err) dual_error = vars[ch][w].thread_err;
        }
        usleep(100000);
    }
    printf("Dual: stopping (code %u).\n", (unsigned)dual_error);
    for (unsigned ch = 0; ch < 2; ch++) for (unsigned w = 0; w < 3; w++)
        if (started[ch][w]) pthread_join(workers[ch][w], NULL);
    for (unsigned ch = 0; ch < 2; ch++) { longmynd_channel = ch; udp_close(); }
    dual_hardware_close();
    return dual_error == ERROR_SIGNAL_TERMINATE ? 0 : dual_error;
}
