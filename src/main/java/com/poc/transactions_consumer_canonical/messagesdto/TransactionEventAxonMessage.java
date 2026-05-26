package com.poc.transactions_consumer_canonical.messagesdto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class TransactionEventAxonMessage {

    // =====================================================
    // ACCOUNT & CARD
    // =====================================================
    private String acctNum;
    private String acctType;
    private String accountHolderName;
    private String accountUri;
    private String accountInformationId;
    private String accountValidationId;
    private String senderAccountNumberType;
    private String receiverAccountNumberType;
    private String paymentAccountReference;

    // =====================================================
    // ACQUIRING
    // =====================================================
    private String acqIca;
    private String acquiringBin;
    private String acquirerReferenceId;
    private String acqReferenceText;
    private String acqRefTxt;
    private String acquiringCountry;
    private String acquiringCountryCd;
    private String acquiringIdentificationCd;
    private String acquiringCredentialId;
    private String acquiringPrcssrId;
    private String acquiringInstitutionId;
    private String visaAcquiringBin;

    // =====================================================
    // TRANSACTION CORE
    // =====================================================
    private String tranId;
    private String tranAmt;
    private String tranAmtCurr;
    private String tranAmtExpnt;
    private String tranAmtCurrNumeric;
    private String tranTypeCd;
    private String tranTypeInd;
    private String tranProcessCd;
    private String tranOrigCountryCd;
    private String tranOrigInstId;
    private String transactionCategoryCode;
    private String transactionLocalDateTime;
    private String transactionLocalDateTimestamp;
    private String transactionOriginationIdentifier;
    private String transactionId;
    private String transactionPurpose;
    private String transactionLinkId;
    private String tranClearDtlId;

    // =====================================================
    // NETWORK
    // =====================================================
    private String networkCode;
    private String networkGateway;
    private String origNetworkGateway;
    private String networkReceiveTimeStamp;
    private String networkResponseCode;
    private String networkResponseCodeDesc;
    private String networkSendTimeStamp;
    private String ntwrkRefNum;
    private String networkReferenceNum;
    private String ntwrkStatDesc;
    private String processedNetwork;

    // =====================================================
    // STATUS & RESPONSE
    // =====================================================
    private String status;
    private String statusReason;
    private String statusTimestamp;
    private String internalStatus;
    private String fundsAvailable;
    private String fundAvailable;
    private String funcCode;
    private String authIdResp;
    private String origNtwrkRespCd;
    private String networkDecisionCode;

    // =====================================================
    // SENDER (SNDR)
    // =====================================================
    private String sndrAcct;
    private String sndrAcctUri;
    private String sndrCardNum;
    private String sndrCardExpirDt;
    private String sndrGovtIdUri;
    private String sndrAddrLine1;
    private String sndrAddrLine2;
    private String sndrAddrStat;
    private String sndrPostCdStat;
    private String sndrBirthCountry;
    private String sndrBirthDt;
    private String sndrCityName;
    private String sndrCountryCd;
    private String sndrCountrySubCd;
    private String sndrDigitalAcctRefNum;
    private String sndrEmailAddr;
    private String sndrFirstName;
    private String sndrLastName;
    private String sndrMiddleName;
    private String sndrNationalityCd;
    private String sndrPaymentFacilitatorId;
    private String sndrSubMerchantId;
    private String sndrPhoneNum;
    private String sndrPostalCd;
    private String sndrSanctionScore;
    private String sndrFromAccount;
    private String sndrOrigName;
    private String senderAliasTypeCd;
    private String senderAliasValTxt;
    private String senderErrorReasonCode;
    private String senderErrorReasonCodeDescription;
    private String senderResponseReasonCode;
    private String senderResponseReasonDetail;
    private int senderEligible;
    private List<GovtId> sndrGovtId;

    // =====================================================
    // RECEIVER (RCVR)
    // =====================================================
    private String rcvrAcct;
    private String rcvrAcctUri;
    private String rcvrCardNum;
    private String rcvrGovtIdUri;
    private String rcvrAddrLine1;
    private String rcvrAddrLine2;
    private String rcvrAddrStat;
    private String rcvrPostCdStat;
    private String rcvrBirthCountry;
    private String rcvrBirthDate;
    private String rcvrCardExpirDt;
    private String rcvrCity;
    private String rcvrCountryCd;
    private String rcvrCountrySubCd;
    private String rcvrEmailAddr;
    private String rcvrExternalAcctRefId;
    private String rcvrExternalRefId;
    private String rcvrFirstName;
    private String rcvrLastName;
    private String rcvrMiddleName;
    private String rcvrNationalityCd;
    private String rcvrPaymentFacilitatorId;
    private String rcvrPhoneNum;
    private String rcvrPostalCd;
    private String rcvrSanctionScore;
    private String rcvrSubMerchantId;
    private String rcvrTokenValue;
    private String rcvrOrigName;
    private String rcvrAliasTypeCd;
    private String rcvrAliasValue;
    private String receiverErrorReasonCode;
    private String receiverErrorReasonCodeDescription;
    private String receiverResponseReasonCode;
    private String receiverResponseReasonDetail;
    private int receiverEligible;
    private List<GovtId> rcvrGovtId;

    // =====================================================
    // CARD ACCEPTOR / MERCHANT
    // =====================================================
    private String cardAcceptorAddrCity;
    private String cardAcceptorAddrCountry;
    private String cardAcceptorAddrPostalCd;
    private String cardAcceptorAddrState;
    private String cardAcceptorAddrStreet;
    private String cardAcceptorIdCd;
    private String cardAcceptorName;
    private String cardAcceptorTerminalId;
    private String cardTypeCode;
    private String merchantType;
    private String merchantCategoryCd;
    private String merchantAdviceCode;
    private String merchantVerificationValue;
    private String merchantVerifyValue;
    private String acceptorTaxId;
    private String acceptorTaxIdName;

    // =====================================================
    // TRANSFER ACCEPTOR
    // =====================================================
    private String transferAccptName;
    private String transferAccptId;
    private String transferAccptTerminalId;
    private String transferAccptAddrLn1;
    private String transferAccptAddrLn2;
    private String transferAccptAddrCity;
    private String transferAccptAddrState;
    private String transferAccptAddrCntry;
    private String transferAccptAddrPostalCd;
    private String transferAcceptorConvenienceAmt;
    private String transferAcceptorConvenienceIndicator;
    private String transferAcceptorPhoneNum;
    private String transferAcceptorMpgId;

    // =====================================================
    // TRANSFER / FUNDING
    // =====================================================
    private String transferId;
    private String transferRef;
    private String transferFailSrcCd;
    private String fundingTranId;
    private String fundingId;
    private String fundingRef;
    private String fundingSource;
    private String mcFundingSource;
    private String fundingStatus;
    private String mappedFundingSource;
    private String fundingIchgFee;

    // =====================================================
    // PAYMENT LEG
    // =====================================================
    private String paymentStatus;
    private String paymentAmt;
    private String paymentCurrCd;
    private String paymentSetlAmt;
    private String paymentSetlCurrCd;
    private String paymentStatusReason;
    private String paymentNetworkCode;
    private String paymentTranProcessCd;
    private String paymentMsgTypeInd;
    private String paymentTransactionCategoryCode;
    private String paymentOrigNtwrkRespCd;
    private String paymentMerchantType;
    private String paymentIchgRateDsgnCd;
    private String paymentNetworkResponseCode;
    private String paymentNetworkResponseDesc;
    private String paymentSysTraceAudNum;
    private String paymentRetrievalRefNum;
    private String paymentSwitchSerialNumber;
    private String paymentUnqRefNum;
    private String paymentAuthIdResp;
    private String paymentNtwrkRefNum;
    private String paymentNetworkSendTimeStamp;
    private String paymentNetworkReceiveTimeStamp;
    private String paymentFundsAvailable;
    private String paymentRef;
    private String paymentTranTypeCd;
    private String paymentId;
    private String paymentQrData;

    // =====================================================
    // FUNDING LEG
    // =====================================================
    private String fundingAmt;
    private String fundingCurrCd;
    private String fundingSetlAmt;
    private String fundingSetlCurrCd;
    private String fundingStatusReason;
    private String fundingNetworkCode;
    private String fundingTranProcessCd;
    private String fundingMsgTypeInd;
    private String fundingTransactionCategoryCode;
    private String fundingOrigNtwrkRespCd;
    private String fundingMerchantType;
    private String fundingIchgRateDsgnCd;
    private String fundingNetworkResponseCode;
    private String fundingNetworkResponseDesc;
    private String fundingSysTraceAudNum;
    private String fundingRetrievalRefNum;
    private String fundingSwitchSerialNumber;
    private String fundingUnqRefNum;
    private String fundingAuthIdResp;
    private String fundingNtwrkRefNum;
    private String fundingNetworkSendTimeStamp;
    private String fundingNetworkReceiveTimeStamp;
    private String fundingFundsAvailable;
    private String fundingQrData;

    // =====================================================
    // SETTLEMENT
    // =====================================================
    private String setlAmt;
    private String setlAmtExpnt;
    private String setlCurrCd;
    private String setlMMDT;
    private String setlServId;
    private String setlDt;
    private String setlIca;
    private String tranFileId;
    private String busnSrvAgmt;

    // =====================================================
    // SPONSOR BANK
    // =====================================================
    private String spnsrBankBusPartnerRefId;
    private String spnsrBankBusnPrtnrId;
    private String spnsrBankName;
    private String spnsrBankBusPrtnrId;
    private String sponsorBankPartnerIdId;
    private String sponsorBankId;
    private String sponsorBankName;

    // =====================================================
    // ORIGINATING INSTITUTION
    // =====================================================
    private String originatingInstitutionRefId;
    private String originatingInstitutionName;
    private String originatingInstName;
    private String originatingInstId;
    private String originatingApiName;
    private String originationCountry;

    // =====================================================
    // INSTITUTION
    // =====================================================
    private String institutionName;
    private String institutionCountry;

    // =====================================================
    // PARTNER
    // =====================================================
    private String partnerId;
    private String partnerNam;
    private String partnerRefId;
    private String partnerVer;

    // =====================================================
    // ERROR
    // =====================================================
    private String errorMessage;
    private String errorReasonCode;
    private String errorCodeDescription;
    private String errorCd;
    private String errorCdDesc;
    private String senderErrorReasonCode2;
    private String receiverErrorReasonCode2;

    // =====================================================
    // AVS / CVC
    // =====================================================
    private String cvcStatus;
    private String cvcResponseCode;
    private String cvcResponseDescription;
    private String cvcRespDesc;
    private String cvcStatCd;
    private String nameStatus;
    private String avsExtRefId;
    private String avsRespCd;
    private String avsRespDesc;

    // =====================================================
    // ADDRESS (AVS)
    // =====================================================
    private String frstNam;
    private String lstNam;
    private String stLn1Addr;
    private String stLn2Addr;
    private String cityNam;
    private String stPrvncCd;
    private String cntryCd;
    private String postCd;
    private String addrStatCd;
    private String postStatCd;

    // =====================================================
    // CLEARING
    // =====================================================
    private String clearingStatus;
    private String clearingDate;
    private String referenceId;
    private String acqRefNumber;
    private String reasonCode;
    private String reasonCodeDesc;
    private String interchangeRate;
    private String interchangeFee;
    private String systemTraceAuditNumber;
    private String corelationId;

    // =====================================================
    // MISC
    // =====================================================
    private String correlationId;
    private String traceId;
    private String createTimestamp;
    private String transmissionDateTime;
    private String processedDt;
    private String cutoffDt;
    private String brand;
    private String acceptanceBrand;
    private String brandProduct;
    private String productType;
    private String channel;
    private String deviceId;
    private String ecommerceInd;
    private String originalEcommerceIndicator;
    private String ucafDowngradeReason;
    private String msgTypeInd;
    private String msgVersion;
    private String custRefNum;
    private String singleDualMessageCd;
    private String singleDualMessageCode;
    private String posAuthDE022;
    private String posAuthDE061;
    private String pointOfServiceInteraction;
    private String switchSerialNumber;
    private String sysTraceAudNum;
    private String retrievalRefNum;
    private String unqRefNum;
    private String ichgFee;
    private String ichgRateDsgnCd;
    private String rateTypeIndicator;
    private String rateFileId;
    private String issuerCountryCd;
    private String issuerName;
    private String issuerIca;
    private String gatewayProcessor;
    private String paymentProcessor;
    private String paymentFacilitatorId;
    private String mdsProcessorId;
    private String processorId;
    private String participationId;
    private String profileId;
    private String serviceIndicator;
    private String initiationSource;
    private String statementDesc;
    private String tokenRequestorId;
    private String tokenRqstrId;
    private String qRData;
    private String onBehalfTranRef;
    private String onBhlfBusnPrtnrId;
    private String origDataElmtText;
    private String origNetworkDataText;
    private String originalNetworkData;
    private String originalRequestPayload;
    private String originalResponsePayload;
    private String bncRequestId;
    private String bncGWRequest;
    private String bncGWResponse;
    private String walletProviderSignature;
    private String openApiClientId;
    private String openApiRqstId;
    private String reversalId;
    private String reversalReference;
    private String reversalReason;
    private String revRsn;
    private String rfndId;
    private String rfndReason;
    private String rfndRef;
    private String reconExcpRsnId;
    private String autoRevSw;
    private String adviceRevRsnCd;
    private String crossborderTransactionIndicator;
    private String cutoffDateEstimateSw;
    private String dbWriteSuccessful;
    private String environmentCode;
    private String dsTransactionId;
    private String programProtocol;
    private String tokenCryptogramCryptoType;
    private String tokenCryptogramCryptoValue;
    private String tokenCryptogramPanSequenceNumber;
    private String authenticationValue;
    private String de126FraudScoreCode;
    private String cardNtwrkBinRngId;
    private String mastercardAssignedId;
    private String mappedCardExpirDt;
    private String mappedCardId;
    private String mappedVisaPanNum;
    private String subMerchantId;
    private String languageDataText;
    private String languageCode;
    private String addtlnProgDataTxt;
    private String availableBalanceAmount;
    private String rqstRefId;
    private String requestId;
    private String refId;
    private String fundVerTxt;
    private String fundingTranId2;
    private String enhancedResponse;
    private String paymentType;
    private String aisAccountType;
    private Long amount;
    private int nonFinTxn;

    // =====================================================
    // AIS — ACCOUNT INFORMATION SERVICE
    // Nested blocks carried under sendingAccountEligible /
    // receivingAccountEligible. Reachable from YAML via dot
    // notation (e.g. "sendingAccountEligible.eligible").
    // =====================================================
    private String partnerName;
    private String currency;
    private String transactionType;
    private String network;
    private String curStat;
    private String origStat;
    private String accountType;
    private String fundsAvailability;
    private AccountEligibility sendingAccountEligible;
    private AccountEligibility receivingAccountEligible;

    // =====================================================
    // UK FPS
    // =====================================================
    private String ukfpsAddrUuid;
    private String ukfpsId;
    private String ukfpsPaymtUuid;
    private String ukfpsReceiverBic;
    private String ukfpsReceiverIban;
    private String ukfpsSenderBic;
    private String ukfpsSendIban;
    private String ukfpsSenderAcctName;
    private String ukfpsSenderSortCd;
    private String ukfpsReceiverAcctName;
    private String ukfpsReceiverSortCd;

    // =====================================================
    // DAILY LIMIT
    // =====================================================
    private String dailyLimitCurrNumTxt;
    private String dailyLimitCurrAmt;
    private String dailyLimitCurrExpNum;
    private String amountInUSD;

    // =====================================================
    // GATEWAY SLI
    // =====================================================
    private String origSliValCd;
    private String gatewaySliModRsnVal;
    private String gatewaySliValCd;
    private String comboCreditDebitIndicatorCode;

}
