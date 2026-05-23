package com.poc.transactions_consumer_canonical.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendTranAddrDtlRequest {

    /** Optional — supply the ID returned from a previous GET to update an existing address.
     *  Omit (or leave null) to create a new address record. */
    private String id;

    private String addrType;
    private String stLine1;
    private String stLine2;
    private String city;
    private String st;
    private String cntryNam;
    private String postCd;
    private String addrStat;
    private String postCdStat;
    private String crteUserNam;
    private String updtUserNam;
}
