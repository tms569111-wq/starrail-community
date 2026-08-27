package com.starrailhearing.web;

import com.starrailhearing.common.exception.AppException;
import com.starrailhearing.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.transaction.CannotCreateTransactionException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    ModelAndView handleAppException(AppException exception) {
        return error(
                exception.getErrorCode().status().value(),
                exception.getErrorCode().name(),
                exception.getMessage()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ModelAndView handleInvalidInput(IllegalArgumentException exception) {
        return error(
                HttpStatus.BAD_REQUEST.value(),
                ErrorCode.INVALID_INPUT.name(),
                exception.getMessage()
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ModelAndView handleMaximumUpload(MaxUploadSizeExceededException exception) {
        return error(
                ErrorCode.TITLE_IMAGE_INVALID.status().value(),
                ErrorCode.TITLE_IMAGE_INVALID.name(),
                "인증 이미지는 2MB 이하여야 합니다."
        );
    }

    @ExceptionHandler({CannotCreateTransactionException.class, TransientDataAccessException.class})
    ModelAndView handleServiceBusy(Exception exception) {
        log.warn("Temporary database capacity failure type={}", exception.getClass().getSimpleName());
        return error(
                ErrorCode.SERVICE_BUSY.status().value(),
                ErrorCode.SERVICE_BUSY.name(),
                ErrorCode.SERVICE_BUSY.defaultMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    ModelAndView handleUnexpected(Exception exception) {
        log.error("Unexpected request failure", exception);
        return error(500, "INTERNAL_ERROR", "예상하지 못한 오류가 발생했습니다.");
    }

    private ModelAndView error(int status, String code, String message) {
        ModelAndView view = new ModelAndView("error/error");
        view.setStatus(HttpStatus.valueOf(status));
        view.addObject("status", status);
        view.addObject("code", code);
        view.addObject("message", message);
        return view;
    }
}
