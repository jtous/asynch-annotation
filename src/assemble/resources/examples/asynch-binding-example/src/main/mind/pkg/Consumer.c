#include <stdio.h>

void METH(in,post)(double value){
	printf("%s %lf \n", ATTR(msg), value);
}
