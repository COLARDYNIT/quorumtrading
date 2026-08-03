package com.quorumtrading.application.usecase;

import com.quorumtrading.application.usecase.query.GetDividendInfoQuery;
import com.quorumtrading.model.valueobject.DividendInfo;

import java.util.Map;

/**
 * Fetch dividend information for a list of tickers.
 * Separates stock from crypto tickers and returns dividend status for each.
 */
public interface GetDividendInfoUseCase {
    Map<String, DividendInfo> execute(GetDividendInfoQuery query);
}
