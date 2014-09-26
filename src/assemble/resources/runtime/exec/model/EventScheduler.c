
typedef struct {
int size;
int start;
int end;
exec_model_ExecuteTask elems[256];
} taskBuffer_t;

taskBuffer_t taskBuff = {257,0,0};

#define nextStartIndex() ((taskBuff.start + 1) % taskBuff.size)
#define nextEndIndex() ((taskBuff.end + 1) % taskBuff.size)
#define isBufferEmpty() (taskBuff.end == taskBuff.start)
#define isBufferFull() (nextEndIndex() == taskBuff.start)
#define bufferWrite(ELEM) \
taskBuff.elems[taskBuff.end] = ELEM; \
taskBuff.end = (taskBuff.end + 1) % taskBuff.size; \
if (isBufferEmpty()) { \
taskBuff.start = nextStartIndex(); \
}
#define bufferRead(ELEM) \
ELEM = taskBuff.elems[taskBuff.start]; \
taskBuff.start = nextStartIndex();



int METH(entryPoint,main)(int argc, char** argv) {

	while (1) {
		if (!isBufferEmpty()){
				exec_model_ExecuteTask task;
				bufferRead(task);
				CALL_PTR(task,execute)();
		} else {
			pause();
		}
	}
	return 0;
}

void METH(taskIn,registerIn)(exec_model_ExecuteTask task) {
	if (!isBufferFull())
		bufferWrite(task);
}
