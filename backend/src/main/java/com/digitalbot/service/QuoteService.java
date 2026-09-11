package com.digitalbot.service;

import com.digitalbot.catalog.PlanCatalog;
import com.digitalbot.dto.ExtraLine;
import com.digitalbot.dto.QuoteRequest;
import com.digitalbot.dto.QuoteResult;
import com.digitalbot.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class QuoteService {

    public QuoteResult quote(QuoteRequest request) {
        QuoteRequest body = request == null ? new QuoteRequest(null, null, null, null) : request;
        PlanCatalog.Plan plan = PlanCatalog.find(body.planId());
        if (plan == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Plan no encontrado");
        }
        String billing = body.billing() == null || body.billing().isBlank() ? "mensual" : body.billing();
        boolean yearly = "anual".equals(billing);
        int users = body.users() == null ? 1 : Math.max(1, body.users());
        int amount = yearly ? plan.yearly() : plan.monthly();
        int extraUsers = Math.max(0, users - 1);
        amount += extraUsers * (yearly ? 80 : 8);

        List<ExtraLine> extraLines = new ArrayList<>();
        List<String> extras = body.extras() == null ? List.of() : body.extras();
        for (String key : extras) {
            PlanCatalog.Extra extra = PlanCatalog.EXTRAS.get(key);
            if (extra == null) {
                continue;
            }
            int extraAmount = yearly ? (int) Math.round(extra.monthly() * 12 * 0.8) : extra.monthly();
            amount += extraAmount;
            extraLines.add(new ExtraLine(key, extra.label(), extraAmount));
        }

        return new QuoteResult(
                plan,
                billing,
                users,
                extraLines,
                amount,
                "USD",
                yearly ? "año" : "mes"
        );
    }
}
