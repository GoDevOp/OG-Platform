/**
 * Copyright (C) 2014 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.sesame.bond;

import com.opengamma.sesame.Environment;
import com.opengamma.financial.trade.BondTrade;
import com.opengamma.util.result.Result;

/**
 * Sesame engine function interface for creating calculators for bonds.
 */
public interface BondCalculatorFactory {

  /**
   * Returns a calculator for a specified environment and bond
   * @param env the environment.
   * @param trade the bond trade.
   * @return a calculator for bonds.
   */
  Result<BondCalculator> createCalculator(Environment env, BondTrade trade);

}
