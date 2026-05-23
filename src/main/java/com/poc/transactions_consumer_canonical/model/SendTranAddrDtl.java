package com.poc.transactions_consumer_canonical.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendTranAddrDtl {

    private String id;
    private String tranId;
    private String addrType;
    private String stLine1;
    private String stLine2;
    private String city;
    private String st;
    private String cntryNam;
    private String postCd;
    private String addrStat;
    private String postCdStat;
    private LocalDateTime crteTs;
    private LocalDateTime updtTs;
    private String crteUserNam;
    private String updtUserNam;
    private LocalDateTime rplctnUpdtTs;
}
