#include <stdint.h>

CONSTRUCTOR(){
	PRIVATE.factor = 1;
	PRIVATE.bufferEnd = 0;
}

void METH(fullRate,post)(double value){

	PRIVATE.values[PRIVATE.bufferEnd]=value;
	PRIVATE.bufferEnd = (PRIVATE.bufferEnd+1) % PRIVATE.factor;

	if (PRIVATE.bufferEnd  == 0){
		int i;
		value=0;
		for (i=0; i<PRIVATE.factor; i++) {
			value+=PRIVATE.values[i];
		}
		value = value / PRIVATE.factor;
		CALL(decimated,post)(value);
	}
}

uint32_t METH(factor, setAndCheck)(uint32_t factor) {
	if (factor < BUFFER_SIZE)
		PRIVATE.factor = factor;
	else
		PRIVATE.factor = BUFFER_SIZE;

	return PRIVATE.factor;
}
