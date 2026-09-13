package com.bc.bcblog.service;

/** IP 定位服务：调用百度普通 IP 定位接口，返回可读位置信息。 */
public interface IpLocationService {

    /**
     * 根据 IP 查询大致位置。
     *
     * @param ip 待查询 IP，为空时查询调用方出口 IP
     * @return 可读位置字符串，例如“北京市”
     */
    String query(String ip);
}
