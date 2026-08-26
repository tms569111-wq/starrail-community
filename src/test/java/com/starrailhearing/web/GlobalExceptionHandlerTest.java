package com.starrailhearing.web;

import com.starrailhearing.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void DB_연결을_잠시_얻지_못하면_500대신_재시도_가능한_503을_보낸다() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var view = handler.handleServiceBusy(
                new CannotCreateTransactionException("connection pool exhausted")
        );

        assertThat(view.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(view.getModel().get("code")).isEqualTo(ErrorCode.SERVICE_BUSY.name());
        assertThat(view.getModel().get("message")).isEqualTo(ErrorCode.SERVICE_BUSY.defaultMessage());
    }
}
