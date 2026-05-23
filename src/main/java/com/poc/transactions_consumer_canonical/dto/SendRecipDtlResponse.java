package com.poc.transactions_consumer_canonical.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendRecipDtlResponse {

    private String tranId;

    // ── Sender ───────────────────────────────────────────────
    private String sendFirstNam;
    private String sendMidNam;
    private String sendLstNam;
    private String sendPhn;
    private String sendEmail;
    private LocalDate sendDob;
    private String sendNatl;
    private String sendBirthCntryNam;
    private String sendAcctNum;
    private String sendAcctUri;
    private String sendGovtIdUri;
    private String sendAcctNumType;
    private String sendCardNum;
    private String sendCardExpirDt;
    private String sendStLine1;
    private String sendStLine2;
    private String sendCity;
    private String sendSt;
    private String sendCntryNam;
    private String sendPostCd;

    // ── Recipient ────────────────────────────────────────────
    private String recipFirstNam;
    private String recipMidNam;
    private String recipLstNam;
    private String recipPhn;
    private String recipEmail;
    private LocalDate recipDob;
    private String recipNatl;
    private String recipBirthCntryNam;
    private String recipAcctNum;
    private String recipAcctUri;
    private String recipGovtIdUri;
    private String recipAcctNumType;
    private String recipCardNum;
    private String recipCardExpirDt;
    private String recipStLine1;
    private String recipStLine2;
    private String recipCity;
    private String recipSt;
    private String recipCntryNam;
    private String recipPostCd;

    // ── Audit ────────────────────────────────────────────────
    private LocalDateTime crteTs;
    private String crteUserNam;
    private LocalDateTime updtTs;
    private String updtUserNam;
    private LocalDateTime tranCrteDt;
    private LocalDateTime rplctnUpdtTs;
}
