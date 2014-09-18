/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.sesame.irfuture;

import java.util.HashMap;
import java.util.Map;

import com.opengamma.analytics.financial.forex.method.FXMatrix;
import com.opengamma.analytics.financial.provider.curve.CurveBuildingBlockBundle;
import com.opengamma.analytics.financial.provider.description.interestrate.MulticurveProviderDiscount;
import com.opengamma.financial.analytics.conversion.FixedIncomeConverterDataProvider;
import com.opengamma.financial.analytics.conversion.InterestRateFutureTradeConverter;
import com.opengamma.financial.analytics.curve.CurveDefinition;
import com.opengamma.financial.analytics.timeseries.HistoricalTimeSeriesBundle;
import com.opengamma.financial.security.FinancialSecurity;
import com.opengamma.sesame.CurveDefinitionFn;
import com.opengamma.sesame.DiscountingMulticurveCombinerFn;
import com.opengamma.sesame.Environment;
import com.opengamma.sesame.HistoricalTimeSeriesFn;
import com.opengamma.financial.trade.InterestRateFutureTrade;
import com.opengamma.util.ArgumentChecker;
import com.opengamma.util.result.Result;
import com.opengamma.util.tuple.Pair;

/**
 * Default factory for interest rate future calculators that provides the converter used to convert the security to an
 * OG-Analytics representation.
 */
public class InterestRateFutureDiscountingCalculatorFactory implements InterestRateFutureCalculatorFactory {

  private final InterestRateFutureTradeConverter _converter;

  private final FixedIncomeConverterDataProvider _definitionToDerivativeConverter;

  private final DiscountingMulticurveCombinerFn _discountingMulticurveCombinerFn;

  private final HistoricalTimeSeriesFn _htsFn;

  private final CurveDefinitionFn _curveDefinitionFn;

  /**
   * Constructs a factory that creates discounting calculators for STIR futures.
   *
   * @param converter the converter used to convert the OG-Financial STIR future to the OG-Analytics definition.
   * @param definitionToDerivativeConverter the converter used to convert the definition to derivative.
   * @param discountingMulticurveCombinerFn the multicurve function.
   * @param curveDefinitionFn the curve definitionFn function.
   * @param htsFn the historical time series function.
   */


  public InterestRateFutureDiscountingCalculatorFactory(InterestRateFutureTradeConverter converter,
      FixedIncomeConverterDataProvider definitionToDerivativeConverter,
      DiscountingMulticurveCombinerFn discountingMulticurveCombinerFn,
      CurveDefinitionFn curveDefinitionFn,
      HistoricalTimeSeriesFn htsFn) {
    _converter = ArgumentChecker.notNull(converter, "converter");
    _definitionToDerivativeConverter =
        ArgumentChecker.notNull(definitionToDerivativeConverter, "definitionToDerivativeConverter");
    _discountingMulticurveCombinerFn = 
        ArgumentChecker.notNull(discountingMulticurveCombinerFn, "discountingMulticurveCombinerFn");
    _curveDefinitionFn = ArgumentChecker.notNull(curveDefinitionFn, "curveDefinitionFn");
    _htsFn = ArgumentChecker.notNull(htsFn, "htsFn");
  }

  @Override
  public Result<InterestRateFutureCalculator> createCalculator(Environment env, InterestRateFutureTrade trade) {

    Result<Boolean> result = Result.success(true);

    MulticurveProviderDiscount multicurveBundle = null;
    Map<String, CurveDefinition> curveDefinitions = new HashMap<>();
    FinancialSecurity security = trade.getSecurity();

    Result<Pair<MulticurveProviderDiscount, CurveBuildingBlockBundle>> bundleResult =
        _discountingMulticurveCombinerFn.createMergedMulticurveBundle(env, trade, new FXMatrix());

    Result<HistoricalTimeSeriesBundle> fixingsResult = _htsFn.getFixingsForSecurity(env, security);

    if (Result.allSuccessful(bundleResult, fixingsResult)) {

      HistoricalTimeSeriesBundle fixings = fixingsResult.getValue();
    
      if (Result.anyFailures(bundleResult, fixingsResult)) {

        result = Result.failure(bundleResult, fixingsResult);

      } else {

        multicurveBundle = bundleResult.getValue().getFirst();
        fixings = fixingsResult.getValue();

        CurveBuildingBlockBundle buildingBlockBundle = bundleResult.getValue().getSecond();
        for (String curveName : buildingBlockBundle.getData().keySet()) {
          Result<CurveDefinition> curveDefinition = _curveDefinitionFn.getCurveDefinition(curveName);

          if (curveDefinition.isSuccess()) {
            curveDefinitions.put(curveName, curveDefinition.getValue());
          } else {
            result = Result.failure(result, Result.failure(curveDefinition));
          }
        }
      }
      if (result.isSuccess()) {
        InterestRateFutureCalculator calculator =
            new InterestRateFutureDiscountingCalculator(trade,
                                                        multicurveBundle,
                                                        curveDefinitions,
                                                        _converter,
                                                        env.getValuationTime(),
                                                        _definitionToDerivativeConverter,
                                                        fixings);

        return Result.success(calculator);
      } else {
        return Result.failure(result);
      }
    } else {
        return Result.failure(bundleResult, fixingsResult);
    }

  }
}