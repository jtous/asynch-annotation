#include <stdint.h>
#include <unistd.h>
#include <math.h>
#include <limits.h>
#include <stdio.h>
#include <sys/time.h>
#include <signal.h>

#define ONE_SECOND (1000000)
#define SIN_FREQ (50)

uint32_t inputRate = 800;
uint32_t decimatedRate = 100;
uint64_t time = 0;
uint32_t period = ONE_SECOND / SIN_FREQ;



void run(int signum);

CONSTRUCTOR() {
    struct itimerval new_timeset;
    long    n_sec = 0 ;
    int n_usecs = ONE_SECOND/inputRate;
	signal(SIGALRM, run);

    new_timeset.it_interval.tv_sec  = n_sec;        /* set reload       */
    new_timeset.it_interval.tv_usec = n_usecs;      /* new ticker value */
    new_timeset.it_value.tv_sec     = n_sec  ;      /* store this       */
    new_timeset.it_value.tv_usec    = n_usecs ;     /* and this         */

	if ( setitimer(ITIMER_REAL, &new_timeset, NULL) == -1 )
		perror("setitimer");
}


void run(int signum) {
	double value;
	CALL(decimationFactor,setAndCheck)(inputRate/decimatedRate);
	time += ONE_SECOND / inputRate;
	value = 100 * sin(((double)time) / period) + (30.0 * rand())/INT_MAX -15 ; //30 percent noise over a sinus
	CALL(out,post)(value);
	printf("%s %lf \n", ATTR(msg), value);
}
