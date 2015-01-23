package com.kylinolap.job.cube;

import com.kylinolap.job.common.MapReduceExecutable;
import com.kylinolap.job.constant.ExecutableConstants;
import com.kylinolap.job.dao.ExecutablePO;
import com.kylinolap.job.execution.ExecutableContext;
import com.kylinolap.job.execution.ExecutableState;
import com.kylinolap.job.execution.ExecuteResult;
import com.kylinolap.job.execution.Output;
import com.kylinolap.job.execution.AbstractExecutable;
import com.kylinolap.job.execution.DefaultChainedExecutable;
import org.apache.commons.lang3.tuple.Pair;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;

/**
 * Created by qianzhou on 12/25/14.
 */
public class CubingJob extends DefaultChainedExecutable {

    public CubingJob() {
        super();
    }

    private static final String CUBE_INSTANCE_NAME = "cubeName";
    private static final String SEGMENT_ID = "segmentId";
    public static final String MAP_REDUCE_WAIT_TIME = "mapReduceWaitTime";


    void setCubeName(String name) {
        setParam(CUBE_INSTANCE_NAME, name);
    }

    public String getCubeName() {
        return getParam(CUBE_INSTANCE_NAME);
    }

    void setSegmentId(String segmentId) {
        setParam(SEGMENT_ID, segmentId);
    }

    public String getSegmentId() {
        return getParam(SEGMENT_ID);
    }

    @Override
    protected Pair<String, String> formatNotifications(ExecutableState state) {
        final Output output = jobService.getOutput(getId());
        String logMsg;
        switch (output.getState()) {
            case ERROR:
                logMsg = output.getVerboseMsg();
                break;
            case DISCARDED:
                logMsg = "";
                break;
            case SUCCEED:
                logMsg = "";
                break;
            default:
                return null;
        }
        String content = ExecutableConstants.NOTIFY_EMAIL_TEMPLATE;
        content = content.replaceAll("\\$\\{job_name\\}", getName());
        content = content.replaceAll("\\$\\{result\\}", state.toString());
        content = content.replaceAll("\\$\\{cube_name\\}", getCubeName());
        content = content.replaceAll("\\$\\{start_time\\}", new Date(getStartTime()).toString());
        content = content.replaceAll("\\$\\{duration\\}", getDuration() / 60000 + "mins");
        content = content.replaceAll("\\$\\{mr_waiting\\}", getMapReduceWaitTime() / 60000 + "mins");
        content = content.replaceAll("\\$\\{last_update_time\\}", new Date(getLastModified()).toString());
        content = content.replaceAll("\\$\\{submitter\\}", getSubmitter());
        content = content.replaceAll("\\$\\{error_log\\}", logMsg);

        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            content = content.replaceAll("\\$\\{job_engine\\}", inetAddress.getCanonicalHostName());
        } catch (UnknownHostException e) {
            logger.warn(e.getLocalizedMessage(), e);
        }

        String title = "["+ state.toString() + "] - [Kylin Cube Build Job]-" + getCubeName();
        return Pair.of(title, content);
    }

    @Override
    protected void onExecuteFinished(ExecuteResult result, ExecutableContext executableContext) {
        long time = 0L;
        for (AbstractExecutable task: getTasks()) {
            final ExecutableState status = task.getStatus();
            if (status != ExecutableState.SUCCEED) {
                break;
            }
            if (task instanceof MapReduceExecutable) {
                time += ((MapReduceExecutable) task).getMapReduceWaitTime();
            }
        }
        setMapReduceWaitTime(time);
        super.onExecuteFinished(result, executableContext);
    }

    public long getMapReduceWaitTime() {
        return getExtraInfoAsLong(MAP_REDUCE_WAIT_TIME, 0L);
    }

    public void setMapReduceWaitTime(long t) {
        addExtraInfo(MAP_REDUCE_WAIT_TIME, t + "");
    }
}