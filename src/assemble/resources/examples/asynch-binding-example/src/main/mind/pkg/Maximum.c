#include <stdio.h>

CONSTRUCTOR(){
	PRIVATE.maxValue = 0.0;
}

void METH(in,post)(double value) {
	if (value > PRIVATE.maxValue ) {
		PRIVATE.maxValue = value;
	}
	printf("%s %lf \n", ATTR(msg), PRIVATE.maxValue);
	CALL(out,post)(value);
}
