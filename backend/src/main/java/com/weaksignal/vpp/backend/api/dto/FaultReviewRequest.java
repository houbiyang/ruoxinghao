package com.weaksignal.vpp.backend.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 兼容复核请求。
 *
 * @param reason 复核原因
 */
public record FaultReviewRequest(
        @NotBlank(message = "复核原因不能为空") String reason
) {
}
