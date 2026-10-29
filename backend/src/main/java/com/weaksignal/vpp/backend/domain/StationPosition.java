package com.weaksignal.vpp.backend.domain;

/**
 * 场站场景坐标。
 *
 * @param x 水平百分比
 * @param y 垂直百分比
 */
public record StationPosition(
        double x,
        double y
) {
}

