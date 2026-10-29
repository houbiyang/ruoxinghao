package com.weaksignal.vpp.simulator;

/**
 * 模拟器启动入口。
 */
public final class SimulatorApplication {

    private SimulatorApplication() {
    }

    /**
     * 启动模拟器。
     *
     * @param args 启动参数
     * @throws Exception 启动异常
     */
    public static void main(String[] args) throws Exception {
        SimulationConfig config = SimulationConfig.fromEnvironment();
        SimulationEngine engine = new SimulationEngine(config);
        MqttSimulationClient client = new MqttSimulationClient(config, engine);
        client.start();
    }
}

