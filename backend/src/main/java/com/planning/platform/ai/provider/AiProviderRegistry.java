package com.planning.platform.ai.provider;

import com.planning.platform.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiProviderRegistry {

    private final List<AiProvider> providers;

    public AiProvider require(String providerCode) {
        return providers.stream()
                .filter(provider -> provider.supports(providerCode))
                .findFirst()
                .orElseThrow(() -> new BizException(422, "不支持的 AI 模型供应商：" + providerCode));
    }
}
