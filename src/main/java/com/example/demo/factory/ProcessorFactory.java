package com.example.demo.factory;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.ControllerMatch;
import com.example.demo.enums.TypeAction;
import com.example.demo.service.ControllerProcessor;
import com.example.demo.service.UnitTestProcessor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class ProcessorFactory {

    private final ControllerProcessor controllerProcessor;
    private final UnitTestProcessor unitTestProcessor;

    public ProcessorFactory(ControllerProcessor controllerProcessor, UnitTestProcessor unitTestProcessor) {
        this.controllerProcessor = controllerProcessor;
        this.unitTestProcessor = unitTestProcessor;
    }

    public ActionProcessor getProcessor(String type) {
        try {
            TypeAction typeAction = TypeAction.fromString(type);

            if (typeAction == TypeAction.UNIT_TEST) {
                return new UnitTestActionProcessor();
            } else {
                return new ControllerActionProcessor();
            }
        } catch (UnsupportedOperationException e) {
            // Se o tipo não for suportado, usa o controller como padrão
            return new ControllerActionProcessor();
        }
    }

    public interface ActionProcessor {
        Mono<ResponseEntity<ApiResponse>> process(List<ControllerMatch> matches, String scope, String path);
    }

    private class ControllerActionProcessor implements ActionProcessor {
        @Override
        public Mono<ResponseEntity<ApiResponse>> process(List<ControllerMatch> matches, String scope, String path) {
            return controllerProcessor.processControllerLogic(matches, scope, path);
        }
    }

    private class UnitTestActionProcessor implements ActionProcessor {
        @Override
        public Mono<ResponseEntity<ApiResponse>> process(List<ControllerMatch> matches, String scope, String path) {
            return unitTestProcessor.processUnitTest(
                    matches.stream()
                            .filter(m -> m.scopeFound())
                            .findFirst()
                            .orElse(null),
                    scope,
                    path);
        }
    }
}
