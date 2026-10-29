package com.weaksignal.vpp.backend.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 调度执行结果。
 *
 * @param batchId 批次标识
 * @param createdAt 创建时间
 * @param commands 已创建指令
 */
public record DispatchExecuteResponse(
        String batchId,
        OffsetDateTime createdAt,
        List<DispatchCommandResponse> commands
) {
}
