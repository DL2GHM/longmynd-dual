/* Test real tuner/demod retune functions against an in-memory I2C register bus. */
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include "dual_hardware.h"
#include "stv0910.h"
#include "stv0910_regs.h"
#include "stv6120_regs.h"

static unsigned writes[65536], tuner_writes[256];
static uint8_t registers[65536];
extern uint8_t rdiv;
uint64_t monotonic_ms(void) {
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC,&ts);
    return ts.tv_sec*1000ULL + ts.tv_nsec/1000000;
}
uint8_t nim_write_demod(uint16_t reg, uint8_t val) { registers[reg]=val; writes[reg]++; return 0; }
uint8_t nim_read_demod(uint16_t reg, uint8_t *val) {
    if (reg==0xf100) *val=0x51;
    else if (reg==0xf101) *val=0x20;
    else *val=registers[reg];
    return 0;
}
uint8_t nim_write_tuner(uint8_t reg, uint8_t val) { (void)val; tuner_writes[reg]++; return 0; }
uint8_t nim_read_tuner(uint8_t reg, uint8_t *val) { (void)reg; *val=1; return 0; }

int main(void) {
    assert(stv0910_init(1500,1500,1.5,1.5)==0);
    assert(registers[RSTV0910_P1_AGC1QUAD]==0xfd);
    assert(registers[RSTV0910_TSTTNR1] & 0x02);
    assert(registers[RSTV0910_TSTTNR3] & 0x02);
    assert(stv0910_init(1500,0,1.5,1.5)==0);
    assert(registers[RSTV0910_P1_AGC1QUAD]==0x05);
    assert(!(registers[RSTV0910_TSTTNR1] & 0x02));
    rdiv=1;
    longmynd_config_t config[2] = {0};
    for (int ch=0;ch<2;ch++) {
        config[ch].freq_requested[0]=741538+ch*1000;
        config[ch].sr_requested[0]=1500;
        config[ch].halfscan_ratio=1.5;
    }
    for (unsigned ch=0;ch<2;ch++) {
        memset(writes,0,sizeof(writes)); memset(tuner_writes,0,sizeof(tuner_writes));
        assert(dual_hardware_retune(ch,config)==0);
        unsigned peer_start=ch ? 0xf200 : 0xf400;
        for (unsigned r=peer_start;r<peer_start+0x200;r++) assert(!writes[r]);
        if (ch) {
            assert(!tuner_writes[STV6120_CTRL2]);
            for (unsigned r=STV6120_CTRL3;r<=STV6120_STAT1;r++) assert(!tuner_writes[r]);
            assert(registers[RSTV0910_P1_SFRINIT1]==2);
            assert(registers[RSTV0910_P1_SFRINIT0]==0xd8);
        } else {
            for (unsigned r=STV6120_CTRL11;r<=STV6120_STAT2;r++) assert(!tuner_writes[r]);
            assert(registers[RSTV0910_P2_SFRINIT1]==2);
            assert(registers[RSTV0910_P2_SFRINIT0]==0xd8);
        }
    }
    puts("PASS: per-channel retune leaves peer demod/PLL registers untouched; SR byte order correct.");
    return 0;
}
