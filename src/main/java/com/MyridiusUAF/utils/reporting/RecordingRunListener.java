package com.MyridiusUAF.utils.reporting;

import com.MyridiusUAF.utils.core.VideoRecorder;
import org.testng.IExecutionListener;

public class RecordingRunListener implements IExecutionListener {
    @Override
    public void onExecutionStart() {
        // Optional: start immediately; we also start lazily after first get(url) if you prefer
    }

    @Override
    public void onExecutionFinish() {
        VideoRecorder.INSTANCE.stopRunIfRunning();
    }
}
