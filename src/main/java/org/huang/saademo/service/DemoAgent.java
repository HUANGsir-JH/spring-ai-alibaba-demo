package org.huang.saademo.service;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;

public interface DemoAgent {

    String agentInvoke(String input) throws GraphRunnerException;
    
    ReactAgent genAgent();
    
    ReactAgent genWriterAgent();
}
