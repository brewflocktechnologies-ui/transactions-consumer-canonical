-- =====================================================================
-- Reference DDL — V1
-- Creates the SEND_TXN_OWNER tables and indexes.
--
-- Prerequisites (one-time, DBA only — not part of application startup):
--   ALTER USER SEND_TXN_OWNER IDENTIFIED BY <secret>;
--   GRANT CREATE SESSION, CREATE TABLE, CREATE INDEX, UNLIMITED TABLESPACE
--     TO SEND_TXN_OWNER;
--
-- These statements are intentionally absent here because schema/user
-- provisioning is managed outside the application by DBA/database tooling.
-- See src/main/resources/sql/init.sql for the DBA bootstrap reference.
-- =====================================================================

-- ──────────────────────────────────────────────────────────────────────
-- SEND_TRANSACTIONS (parent)
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE SEND_TXN_OWNER.SEND_TRANSACTIONS (
    TRAN_ID                VARCHAR2(50) PRIMARY KEY,
    TRAN_INIT_ID           VARCHAR2(50),
    ORIG_INST_ID           VARCHAR2(50),
    ORIG_INST_NAM          VARCHAR2(250),
    TRANFR_ACPT_NAM        VARCHAR2(250),
    TRANFR_ACPT_ID         VARCHAR2(40),
    TRAN_CRTE_DT           TIMESTAMP NOT NULL,
    TRAN_TYPE              VARCHAR2(10) NOT NULL,
    CUST_REF_NUM           VARCHAR2(100),
    CUR_STAT               VARCHAR2(20),
    ORIG_STAT              VARCHAR2(20),
    USE_CASE               VARCHAR2(3),
    MSG_TYPE               VARCHAR2(10),
    REF_ID                 VARCHAR2(40),
    SW_SER_NUM             VARCHAR2(20),
    BNKNT_REF_NUM          VARCHAR2(1000),
    SEND_ACCT              VARCHAR2(100),
    RECIP_ACCT             VARCHAR2(100),
    TRAN_AMT               NUMBER(22,2),
    TRAN_CURR              VARCHAR2(3),
    ERR_CD                 VARCHAR2(25),
    FUND_AVAIL             VARCHAR2(30),
    CORLTN_ID              VARCHAR2(40),
    CRTE_TS                TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP,
    CRTE_USER_NAM          VARCHAR2(20),
    UPDT_TS                TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP,
    UPDT_USER_NAM          VARCHAR2(20),
    RPLCTN_UPDT_TS         TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    NTWRK_CD               VARCHAR2(40),
    NTWRK_RESP_CD          VARCHAR2(30),
    TRAN_INIT_NAM          VARCHAR2(250),
    NAM_STAT               VARCHAR2(20),
    CVC_STAT               VARCHAR2(20),
    CVC_RESP_CD            VARCHAR2(5),
    ACCT_NUM               VARCHAR2(50),
    ACCT_TYPE              VARCHAR2(20),
    ACCT_HOLD_NAM          VARCHAR2(250),
    ERR_CD_DESC            VARCHAR2(500),
    NON_FIN_TXN            NUMBER(1,0) DEFAULT 0 NOT NULL,
    RECIP_ELIG             NUMBER(1,0),
    NTWRK_RESP_CD_DESC     VARCHAR2(500)
);

CREATE INDEX IDX_SEND_TXN_TRAN_INIT_ID  ON SEND_TXN_OWNER.SEND_TRANSACTIONS (TRAN_INIT_ID);
CREATE INDEX IDX_SEND_TXN_REF_ID        ON SEND_TXN_OWNER.SEND_TRANSACTIONS (REF_ID);
CREATE INDEX IDX_SEND_TXN_LOWER_REF_ID  ON SEND_TXN_OWNER.SEND_TRANSACTIONS (LOWER(REF_ID));
CREATE INDEX IDX_SEND_TXN_TRAN_CRTE_DT  ON SEND_TXN_OWNER.SEND_TRANSACTIONS (TRAN_CRTE_DT);
CREATE INDEX IDX_SEND_TXN_STATUS        ON SEND_TXN_OWNER.SEND_TRANSACTIONS (CUR_STAT);
CREATE INDEX IDX_SEND_TXN_STATUSO       ON SEND_TXN_OWNER.SEND_TRANSACTIONS (ORIG_STAT);
CREATE INDEX IDX_SEND_TXN_NETWORK_CD    ON SEND_TXN_OWNER.SEND_TRANSACTIONS (NTWRK_CD);
CREATE INDEX IDX_SEND_TXN_USE_CASE      ON SEND_TXN_OWNER.SEND_TRANSACTIONS (USE_CASE);
CREATE INDEX IDX_SEND_TXN_CUST_REF_NUM  ON SEND_TXN_OWNER.SEND_TRANSACTIONS (CUST_REF_NUM);

-- ──────────────────────────────────────────────────────────────────────
-- SEND_TRAN_DTL (1:1 child)
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE SEND_TXN_OWNER.SEND_TRAN_DTL (
    TRAN_ID                     VARCHAR2(50) PRIMARY KEY,
    PAYMT_REF                   VARCHAR2(50),
    UNQ_TRAN_REF                VARCHAR2(50),
    ACQ_CNTRY_NAM               VARCHAR2(60),
    ACQ_ICA                     NUMBER(38,0),
    FUND_SRC                    VARCHAR2(50),
    ICHG_RATE_DSGN              VARCHAR2(50),
    MERCH_CAT_CD                VARCHAR2(50),
    PAYMT_TYPE                  VARCHAR2(50),
    POINT_SERV_INTRCTN          VARCHAR2(60),
    TRAN_PRPS                   VARCHAR2(50),
    TRAN_SETL_AMT               NUMBER(22,2),
    BNC_GTWY_RQST               CLOB,
    BNC_GTWY_RESP               CLOB,
    ORIG_RQST_PYLD              CLOB,
    ORIG_RESP_PYLD              CLOB,
    TRANFR_ACPT_ID              VARCHAR2(100),
    TRANFR_ACPT_NAM             VARCHAR2(250),
    MC_ASSGN_MERCH              VARCHAR2(60),
    PAYMT_FACILTR_ID            VARCHAR2(50),
    SUB_MERCH_ID                VARCHAR2(50),
    TRANFR_TRML_ID              VARCHAR2(60),
    TRANFR_ACPT_ST_LINE1        VARCHAR2(250),
    TRANFR_ACPT_ST_LINE2        VARCHAR2(250),
    TRANFR_ACPT_CITY            VARCHAR2(60),
    TRANFR_ACPT_ST              VARCHAR2(60),
    TRANFR_ACPT_CNTRY_NAM       VARCHAR2(60),
    TRANFR_ACPT_POST_CD         VARCHAR2(60),
    CRTE_TS                     TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP,
    CRTE_USER_NAM               VARCHAR2(20),
    UPDT_TS                     TIMESTAMP WITH TIME ZONE,
    UPDT_USER_NAM               VARCHAR2(20),
    EVENT_ID                    VARCHAR2(40),
    EVENT_TS                    TIMESTAMP WITH TIME ZONE,
    EVENT_CORLTN_ID             VARCHAR2(40),
    MSG_VERSION                 VARCHAR2(2),
    TRAN_CRTE_DT                TIMESTAMP NOT NULL,
    RPLCTN_UPDT_TS              TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    TRAN_TYPE_IND_CD            VARCHAR2(5),
    REGULATED_RATE_TYPE_CD      VARCHAR2(5),
    CVC_RESP_DESC               VARCHAR2(500),
    PROC_ID                     VARCHAR2(20),
    ACQ_IDEN_CD                 VARCHAR2(20),
    TRANFR_ACPT_MPG_ID          VARCHAR2(20),
    TRANFR_ACPT_MERCH_VALUE     VARCHAR2(20),
    CONSTRAINT SEND_TRAN_DTL_FK
        FOREIGN KEY (TRAN_ID) REFERENCES SEND_TXN_OWNER.SEND_TRANSACTIONS (TRAN_ID)
);

CREATE INDEX IDX_SEND_TRAN_DTL_TRAN_ID ON SEND_TXN_OWNER.SEND_TRAN_DTL (TRAN_ID);

-- ──────────────────────────────────────────────────────────────────────
-- SEND_RECIP_DTL (1:1 child)
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE SEND_TXN_OWNER.SEND_RECIP_DTL (
    TRAN_ID                    VARCHAR2(50) PRIMARY KEY,
    SEND_FIRST_NAM             VARCHAR2(80),
    SEND_MID_NAM               VARCHAR2(80),
    SEND_LST_NAM               VARCHAR2(80),
    SEND_PHN                   VARCHAR2(20),
    SEND_EMAIL                 VARCHAR2(254),
    SEND_DOB                   DATE,
    SEND_NATL                  VARCHAR2(60),
    SEND_BIRTH_CNTRY_NAM       VARCHAR2(20),
    SEND_ACCT_NUM              VARCHAR2(50),
    SEND_ACCT_URI              VARCHAR2(60),
    SEND_GOVT_ID_URI           CLOB,
    SEND_ACCT_NUM_TYPE         VARCHAR2(10),
    SEND_CARD_NUM              VARCHAR2(50),
    SEND_CARD_EXPIR_DT         VARCHAR2(10),
    SEND_ST_LINE1              VARCHAR2(250),
    SEND_ST_LINE2              VARCHAR2(250),
    SEND_CITY                  VARCHAR2(50),
    SEND_ST                    VARCHAR2(10),
    SEND_CNTRY_NAM             VARCHAR2(20),
    SEND_POST_CD               VARCHAR2(10),
    RECIP_FIRST_NAM            VARCHAR2(80),
    RECIP_MID_NAM              VARCHAR2(80),
    RECIP_LST_NAM              VARCHAR2(80),
    RECIP_PHN                  VARCHAR2(20),
    RECIP_EMAIL                VARCHAR2(254),
    RECIP_DOB                  DATE,
    RECIP_NATL                 VARCHAR2(60),
    RECIP_BIRTH_CNTRY_NAM      VARCHAR2(20),
    RECIP_ACCT_NUM             VARCHAR2(50),
    RECIP_ACCT_URI             VARCHAR2(60),
    RECIP_GOVT_ID_URI          CLOB,
    RECIP_ACCT_NUM_TYPE        VARCHAR2(10),
    RECIP_CARD_NUM             VARCHAR2(50),
    RECIP_CARD_EXPIR_DT        VARCHAR2(10),
    RECIP_ST_LINE1             VARCHAR2(250),
    RECIP_ST_LINE2             VARCHAR2(250),
    RECIP_CITY                 VARCHAR2(50),
    RECIP_ST                   VARCHAR2(10),
    RECIP_CNTRY_NAM            VARCHAR2(20),
    RECIP_POST_CD              VARCHAR2(10),
    CRTE_TS                    TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP,
    CRTE_USER_NAM              VARCHAR2(20),
    UPDT_TS                    TIMESTAMP WITH TIME ZONE,
    UPDT_USER_NAM              VARCHAR2(20),
    TRAN_CRTE_DT               TIMESTAMP NOT NULL,
    RPLCTN_UPDT_TS             TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT SEND_RECIP_DTL_FK
        FOREIGN KEY (TRAN_ID) REFERENCES SEND_TXN_OWNER.SEND_TRANSACTIONS (TRAN_ID)
);

CREATE INDEX IDX_SEND_RECIP_EMAIL    ON SEND_TXN_OWNER.SEND_RECIP_DTL (SEND_EMAIL);
CREATE INDEX IDX_SEND_RECIP_PHN      ON SEND_TXN_OWNER.SEND_RECIP_DTL (SEND_PHN);
CREATE INDEX IDX_SEND_RECIP_ACCT_URI ON SEND_TXN_OWNER.SEND_RECIP_DTL (SEND_ACCT_URI);

-- ──────────────────────────────────────────────────────────────────────
-- SEND_TRAN_ADDR_DTL (1:many child)
-- ──────────────────────────────────────────────────────────────────────
CREATE TABLE SEND_TXN_OWNER.SEND_TRAN_ADDR_DTL (
    ID                     VARCHAR2(50) PRIMARY KEY,
    TRAN_ID                VARCHAR2(50),
    ADDR_TYPE              VARCHAR2(50),
    ST_LINE1               VARCHAR2(250),
    ST_LINE2               VARCHAR2(250),
    CITY                   VARCHAR2(60),
    ST                     VARCHAR2(60),
    CNTRY_NAM              VARCHAR2(60),
    POST_CD                VARCHAR2(60),
    ADDR_STAT              VARCHAR2(20),
    POST_CD_STAT           VARCHAR2(20),
    CRTE_TS                TIMESTAMP DEFAULT SYSTIMESTAMP,
    UPDT_TS                TIMESTAMP,
    CRTE_USER_NAM          VARCHAR2(20),
    UPDT_USER_NAM          VARCHAR2(20),
    RPLCTN_UPDT_TS         TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT SEND_TRAN_ADDR_DTL_FK1
        FOREIGN KEY (TRAN_ID) REFERENCES SEND_TXN_OWNER.SEND_TRANSACTIONS (TRAN_ID)
);

CREATE INDEX IDX_SEND_TRAN_ADDR_TRAN_ID ON SEND_TXN_OWNER.SEND_TRAN_ADDR_DTL (TRAN_ID);
CREATE INDEX IDX_SEND_TRAN_ADDR_TYPE    ON SEND_TXN_OWNER.SEND_TRAN_ADDR_DTL (ADDR_TYPE);
