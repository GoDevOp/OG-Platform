/**
 * Copyright (C) 2013 - present by OpenGamma Inc. and the OpenGamma group of companies
 * 
 * Please see distribution for license.
 */
package com.opengamma.analytics.financial.model.volatility;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;
import org.threeten.bp.LocalDate;

import com.opengamma.analytics.math.matrix.DoubleMatrix1D;
import com.opengamma.analytics.util.time.TimeCalculator;

/**
 * 
 */
public class BlackImpliedVolatilityWithGreeksTest {

  private static final double EPS = 1.e-8;
  private static final BlackImpliedVolatilityWithGreeks calculator = new BlackImpliedVolatilityWithGreeks();

  private static final double TIME_TO_EXPIRY = 4.5;
  private static final double FORWARD = 104;
  private static final double[] STRIKE = new double[] {85.0, 90.0, 95.0, 100.0, 103.0, 108.0, 120.0, 150.0, 250.0 };
  private static final double[] VOL = new double[] {0.1, 0.12, 0.15, 0.2, 0.3, 0.5, 0.8, 1.2 };
  private static final double[] INTEREST_RATE = new double[] {0.01, 0.03, 0.05, 0.1 };

  private static final int nStrikes = STRIKE.length;
  private static final int nVols = VOL.length;
  private static final int nRates = INTEREST_RATE.length;

  private static final double[] SPOT = new double[nRates];
  static {
    for (int i = 0; i < nRates; ++i) {
      SPOT[i] = getSpot(FORWARD, TIME_TO_EXPIRY, INTEREST_RATE[i]);
    }
  }

  /**
   * Check implied volatility agrees with the original volatility which is used for computing the sample option price. 
   * Then check the resulting greeks are also consistent.  
   */
  @Test
  public void recoveryTest() {
    final boolean[] isCall = new boolean[] {true, false };
    for (final boolean call : isCall) {
      for (int i = 0; i < nStrikes; ++i) {
        for (int j = 0; j < nVols; ++j) {
          for (int k = 0; k < nRates; ++k) {
            final double spotOptionPrice = BlackScholesFormulaRepository.price(SPOT[k], STRIKE[i], TIME_TO_EXPIRY, VOL[j], INTEREST_RATE[k], INTEREST_RATE[k], call);
            final double forwardOptionPrice = BlackFormulaRepository.price(FORWARD, STRIKE[i], TIME_TO_EXPIRY, VOL[j], call);
            final double[] volAndGreeks = calculator.getImpliedVolatilityAndGreeksForward(spotOptionPrice, FORWARD, SPOT[k], STRIKE[i], TIME_TO_EXPIRY, call);
            final double[] volAndGreeksOption = calculator.getImpliedVolatilityAndGreeksForwardOption(spotOptionPrice, forwardOptionPrice, SPOT[k], STRIKE[i], TIME_TO_EXPIRY, call);
            final double[] volAndGreeksDiscount = calculator.getImpliedVolatilityAndGreeksDiscountFactor(spotOptionPrice, Math.exp(-INTEREST_RATE[k] * TIME_TO_EXPIRY), SPOT[k], STRIKE[i],
                TIME_TO_EXPIRY, call);
            assertEquals(volAndGreeks[0], VOL[j], Math.max(1., VOL[j]) * EPS);
            assertEquals(volAndGreeksOption[0], VOL[j], Math.max(1., VOL[j]) * EPS);
            assertEquals(volAndGreeksDiscount[0], VOL[j], Math.max(1., VOL[j]) * EPS);

            final double refDelta = BlackScholesFormulaRepository.delta(SPOT[k], STRIKE[i], TIME_TO_EXPIRY, volAndGreeks[0], INTEREST_RATE[k], INTEREST_RATE[k], call);
            final double refGamma = BlackScholesFormulaRepository.gamma(SPOT[k], STRIKE[i], TIME_TO_EXPIRY, volAndGreeks[0], INTEREST_RATE[k], INTEREST_RATE[k]);
            final double refVega = BlackScholesFormulaRepository.vega(SPOT[k], STRIKE[i], TIME_TO_EXPIRY, volAndGreeks[0], INTEREST_RATE[k], INTEREST_RATE[k]);

            assertEquals(volAndGreeks[1], refDelta, Math.max(1., Math.abs(refDelta)) * EPS);
            assertEquals(volAndGreeks[2], refGamma, Math.max(1., Math.abs(refGamma)) * EPS);
            assertEquals(volAndGreeks[3], refVega, Math.max(1., Math.abs(refVega)) * EPS);
            assertEquals(volAndGreeksOption[1], refDelta, Math.max(1., Math.abs(refDelta)) * EPS);
            assertEquals(volAndGreeksOption[2], refGamma, Math.max(1., Math.abs(refGamma)) * EPS);
            assertEquals(volAndGreeksOption[3], refVega, Math.max(1., Math.abs(refVega)) * EPS);
            assertEquals(volAndGreeksDiscount[1], refDelta, Math.max(1., Math.abs(refDelta)) * EPS);
            assertEquals(volAndGreeksDiscount[2], refGamma, Math.max(1., Math.abs(refGamma)) * EPS);
            assertEquals(volAndGreeksDiscount[3], refVega, Math.max(1., Math.abs(refVega)) * EPS);
          }
        }
      }
    }
  }

  /**
   * 
   */
  @Test
  public void priceTest() {
    final boolean[] isCall = new boolean[] {true, false };
    for (final boolean call : isCall) {
      for (int i = 0; i < nStrikes; ++i) {
        for (int j = 0; j < nVols; ++j) {
          for (int k = 0; k < nRates; ++k) {
            final double spotOptionPrice = BlackScholesFormulaRepository.price(SPOT[k], STRIKE[i], TIME_TO_EXPIRY, VOL[j], INTEREST_RATE[k], INTEREST_RATE[k], call);
            final double[] priceAndGreeks = calculator.getPriceAndGreeksForward(FORWARD, SPOT[k], STRIKE[i], TIME_TO_EXPIRY, VOL[j], call);
            final double[] priceAndGreeksDiscount = calculator.getPriceAndGreeksDiscountFactor(Math.exp(-INTEREST_RATE[k] * TIME_TO_EXPIRY), SPOT[k], STRIKE[i],
                TIME_TO_EXPIRY, VOL[j], call);
            assertEquals(priceAndGreeks[0], spotOptionPrice, Math.max(1., spotOptionPrice) * EPS);
            assertEquals(priceAndGreeksDiscount[0], spotOptionPrice, Math.max(1., spotOptionPrice) * EPS);

            final double refDelta = BlackScholesFormulaRepository.delta(SPOT[k], STRIKE[i], TIME_TO_EXPIRY, VOL[j], INTEREST_RATE[k], INTEREST_RATE[k], call);
            final double refGamma = BlackScholesFormulaRepository.gamma(SPOT[k], STRIKE[i], TIME_TO_EXPIRY, VOL[j], INTEREST_RATE[k], INTEREST_RATE[k]);
            final double refVega = BlackScholesFormulaRepository.vega(SPOT[k], STRIKE[i], TIME_TO_EXPIRY, VOL[j], INTEREST_RATE[k], INTEREST_RATE[k]);

            assertEquals(priceAndGreeks[1], refDelta, Math.max(1., Math.abs(refDelta)) * EPS);
            assertEquals(priceAndGreeks[2], refGamma, Math.max(1., Math.abs(refGamma)) * EPS);
            assertEquals(priceAndGreeks[3], refVega, Math.max(1., Math.abs(refVega)) * EPS);
            assertEquals(priceAndGreeksDiscount[1], refDelta, Math.max(1., Math.abs(refDelta)) * EPS);
            assertEquals(priceAndGreeksDiscount[2], refGamma, Math.max(1., Math.abs(refGamma)) * EPS);
            assertEquals(priceAndGreeksDiscount[3], refVega, Math.max(1., Math.abs(refVega)) * EPS);
          }
        }
      }
    }
  }

  /**
   * Sample data with assumption r = 0.02 due to missing forward values
   */
  @Test
  public void sampleTest() {
    boolean print = false;

    final LocalDate evaluationDate = LocalDate.of(2013, 8, 1);
    final LocalDate[] expiryDate = new LocalDate[] {LocalDate.of(2013, 8, 17), LocalDate.of(2013, 8, 17), LocalDate.of(2013, 8, 17), LocalDate.of(2013, 8, 17),
        LocalDate.of(2013, 8, 17), LocalDate.of(2013, 8, 17), LocalDate.of(2013, 9, 21), LocalDate.of(2013, 9, 21), LocalDate.of(2013, 9, 21),
        LocalDate.of(2013, 9, 21), LocalDate.of(2013, 9, 21) };
    final int nOptions = expiryDate.length;
    final double[] timeToExpiry = new double[nOptions];

    for (int i = 0; i < nOptions; ++i) {
      timeToExpiry[i] = TimeCalculator.getTimeBetween(evaluationDate, expiryDate[i]);
    }

    if (print == true) {
      System.out.println("\t\t\t" + "(Implied Volatility," + "\t" + "Delta," + "\t" + "Gamma," + "\t" + "Vega)");
    }
    final boolean isCall = true;
    final double[] optionPrice = new double[] {21.82, 18.64, 19.50, 16.00, 14.00, 12.00, 12.00, 12.00, 12.00, 12.00, 12.00 };
    final double[] strike = new double[] {1675., 1680., 1685., 1690., 1695., 1700., 1680., 1685., 1690., 1695., 1700. };
    final double spot = 1685.96;
    //    final double interestRate = 0.02; //This value is used to compute discountFactor, forward and forwardOption

    /*
     * These forward prices are exp(r*t)
     */
    final double[] discountFactor = new double[] {0.9991236718712008, 0.9991236718712008, 0.9991236718712008, 0.9991236718712008, 0.9991236718712008, 0.9991236718712008, 0.9972093804899117,
        0.9972093804899117, 0.9972093804899117, 0.9972093804899117, 0.9972093804899117 };
    for (int i = 0; i < nOptions; ++i) {
      final double[] volAndGreeks = calculator.getImpliedVolatilityAndGreeksDiscountFactor(optionPrice[i], discountFactor[i], spot, strike[i], timeToExpiry[i], isCall);
      if (print == true) {
        System.out.print(expiryDate[i] + "; K=" + strike[i] + ": ");
        System.out.println(new DoubleMatrix1D(volAndGreeks));
      }
    }

    if (print == true) {
      System.out.println("\n");
    }

    /*
     * These forward prices are spot*exp(r*t)
     */
    final double[] forward = new double[] {1687.4387500422877, 1687.4387500422877, 1687.4387500422877, 1687.4387500422877, 1687.4387500422877, 1687.4387500422877, 1690.6780391212496,
        1690.6780391212496, 1690.6780391212496, 1690.6780391212496, 1690.6780391212496 };
    for (int i = 0; i < nOptions; ++i) {
      final double[] volAndGreeks = calculator.getImpliedVolatilityAndGreeksForward(optionPrice[i], forward[i], spot, strike[i], timeToExpiry[i], isCall);
      if (print == true) {
        System.out.print(expiryDate[i] + "; K=" + strike[i] + ": ");
        System.out.println(new DoubleMatrix1D(volAndGreeks));
      }
    }

    if (print == true) {
      System.out.println("\n");
    }

    /*
     * These forward option values are (spot option value)*exp(r*t)
     */
    final double[] forwardOption = new double[] {21.839138251158225, 18.656349083482553, 19.517103386690437, 16.014033548053693, 14.012279354546981, 12.01052516104027, 12.033581146323161,
        12.033581146323161, 12.033581146323161, 12.033581146323161, 12.033581146323161 };
    for (int i = 0; i < nOptions; ++i) {
      final double[] volAndGreeks = calculator.getImpliedVolatilityAndGreeksForwardOption(optionPrice[i], forwardOption[i], spot, strike[i], timeToExpiry[i], isCall);
      if (print == true) {
        System.out.print(expiryDate[i] + "; K=" + strike[i] + ": ");
        System.out.println(new DoubleMatrix1D(volAndGreeks));
      }
    }

  }

  private static double getSpot(final double forward, final double time, final double interestRate) {
    final double df = Math.exp(-interestRate * time);
    return forward * df;
  }

}
