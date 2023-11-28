package run.jkdev.dec4iot.jetpack.interfaces;

public interface QrResult {
    int RESULT_OK = 0; // OKay! No error, result contains data.
    int RESULT_KO = 1; // General Error, result contains throwable.

    int RESULT_NO_DEC4IOT = 10; // Device not supported
}
