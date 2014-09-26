#define BUFFER_SIZE 64

struct {
	uint32_t bufferEnd;
	double values[BUFFER_SIZE];
	uint32_t factor;
}PRIVATE;
