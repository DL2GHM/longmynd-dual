/* Experimental MiniTiouner Pro dual receiver. GPL-3.0-or-later. */
#ifndef DUAL_HARDWARE_H
#define DUAL_HARDWARE_H
#include "main.h"
int dual_hardware_open(const char *master_serial, const char *aux_serial);
uint8_t dual_hardware_start(const longmynd_config_t config[2], bool lna_present[2]);
uint8_t dual_hardware_retune(unsigned channel, const longmynd_config_t config[2]);
uint8_t dual_hardware_report(unsigned channel, longmynd_status_t *status);
void dual_hardware_close(void);
#endif
