/**
 * Copyright (C) 2012 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.financial.generator;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Random;

import javax.time.calendar.DateProvider;
import javax.time.calendar.DayOfWeek;
import javax.time.calendar.ZonedDateTime;
import javax.time.calendar.format.DateTimeFormatter;
import javax.time.calendar.format.DateTimeFormatterBuilder;

import com.opengamma.core.config.ConfigSource;
import com.opengamma.core.historicaltimeseries.HistoricalTimeSeriesSource;
import com.opengamma.core.holiday.HolidaySource;
import com.opengamma.core.region.RegionSource;
import com.opengamma.financial.analytics.ircurve.CurveSpecificationBuilderConfiguration;
import com.opengamma.financial.convention.ConventionBundleSource;
import com.opengamma.id.ExternalScheme;
import com.opengamma.master.exchange.ExchangeMaster;
import com.opengamma.master.security.ManageableSecurity;
import com.opengamma.master.security.SecurityMaster;
import com.opengamma.util.money.Currency;

/**
 * Utility class for constructing parameters to random (but reasonable) securities.
 * 
 * @param <T> the security type, or a common super type if multiple types are being produced
 */
public abstract class SecurityGenerator<T extends ManageableSecurity> {

  /**
   * Format dates.
   */
  public static final DateTimeFormatter DATE_FORMATTER = new DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd").toFormatter();

  /**
   * Format rates.
   */
  public static final DecimalFormat RATE_FORMATTER = new DecimalFormat("0.###%");

  /**
   * Format notionals.
   */
  public static final DecimalFormat NOTIONAL_FORMATTER = new DecimalFormat("0,000");

  /**
   * Constant for the length of a year in days.
   */
  protected static final double YEAR_LENGTH = 365.25;

  private Random _random = new Random();
  private ConventionBundleSource _conventionSource;
  private ConfigSource _configSource;
  private HolidaySource _holidaySource;
  private HistoricalTimeSeriesSource _historicalSource;
  private RegionSource _regionSource;
  private ExchangeMaster _exchangeMaster;
  private SecurityMaster _securityMaster;
  private String _currencyCurveName;
  private ExternalScheme _preferredScheme;

  public Random getRandom() {
    return _random;
  }

  public void setRandom(final Random random) {
    _random = random;
  }

  protected int getRandom(final int n) {
    return getRandom().nextInt(n);
  }

  protected double getRandom(final double low, final double high) {
    return low + (high - low) * getRandom().nextDouble();
  }

  protected <X> X getRandom(final X[] xs) {
    return xs[getRandom(xs.length)];
  }

  protected <X> X getRandom(final List<X> xs) {
    return xs.get(getRandom(xs.size()));
  }

  protected int getRandom(final int[] xs) {
    return xs[getRandom(xs.length)];
  }

  protected double getRandom(final double[] xs) {
    return xs[getRandom(xs.length)];
  }

  public ConventionBundleSource getConventionSource() {
    return _conventionSource;
  }

  public void setConventionSource(final ConventionBundleSource conventionSource) {
    _conventionSource = conventionSource;
  }

  public ConfigSource getConfigSource() {
    return _configSource;
  }

  public void setConfigSource(final ConfigSource configSource) {
    _configSource = configSource;
  }

  public HolidaySource getHolidaySource() {
    return _holidaySource;
  }

  public void setHolidaySource(final HolidaySource holidaySource) {
    _holidaySource = holidaySource;
  }

  public HistoricalTimeSeriesSource getHistoricalSource() {
    return _historicalSource;
  }

  public void setHistoricalSource(final HistoricalTimeSeriesSource historicalSource) {
    _historicalSource = historicalSource;
  }

  public ExchangeMaster getExchangeMaster() {
    return _exchangeMaster;
  }

  public void setExchangeMaster(final ExchangeMaster exchangeMaster) {
    _exchangeMaster = exchangeMaster;
  }

  public RegionSource getRegionSource() {
    return _regionSource;
  }

  public void setRegionSource(final RegionSource regionSource) {
    _regionSource = regionSource;
  }

  public SecurityMaster getSecurityMaster() {
    return _securityMaster;
  }

  public void setSecurityMaster(final SecurityMaster securityMaster) {
    _securityMaster = securityMaster;
  }

  public String getCurrencyCurveName() {
    return _currencyCurveName;
  }

  public void setCurrencyCurveName(final String currencyCurveName) {
    _currencyCurveName = currencyCurveName;
  }

  protected CurveSpecificationBuilderConfiguration getCurrencyCurveConfig(final Currency currency) {
    return getConfigSource().getByName(CurveSpecificationBuilderConfiguration.class, getCurrencyCurveName() + "_" + currency.getCode(), null);
  }

  public ExternalScheme getPreferredScheme() {
    return _preferredScheme;
  }

  public void setPreferredScheme(final ExternalScheme preferredScheme) {
    _preferredScheme = preferredScheme;
  }

  public static Currency[] getDefaultCurrencies() {
    return new Currency[] {Currency.USD, Currency.GBP, Currency.EUR, Currency.JPY, Currency.CHF };
  }

  public Currency[] getCurrencies() {
    return getDefaultCurrencies();
  }

  protected Currency getRandomCurrency() {
    return getRandom(getCurrencies());
  }

  private boolean isWorkday(final DayOfWeek dow, final Currency currency) {
    // TODO: use a proper convention/holiday source
    return dow.getValue() < 6;
  }

  private boolean isHoliday(final DateProvider ldp, final Currency currency) {
    return getHolidaySource().isHoliday(ldp.toLocalDate(), currency);
  }

  /**
   * Returns the date unchanged if this is a working day, otherwise advances the date.
   * 
   * @param zdt the date to consider
   * @param currency the currency identifying the holiday zone
   * @return the original or adjusted date
   */
  // TODO: replace this with a date adjuster
  protected ZonedDateTime nextWorkingDay(ZonedDateTime zdt, final Currency currency) {
    while (!isWorkday(zdt.getDayOfWeek(), currency) || isHoliday(zdt, currency)) {
      zdt = zdt.plusDays(1);
    }
    return zdt;
  }

  /**
   * Returns the date unchanged if this is a working day, otherwise retreats the date.
   * 
   * @param zdt the date to consider
   * @param currency the currency identifying the holiday zone
   * @return the original or adjusted date
   */
  // TODO: replace this with a date adjuster
  protected ZonedDateTime previousWorkingDay(ZonedDateTime zdt, final Currency currency) {
    while (!isWorkday(zdt.getDayOfWeek(), currency) || isHoliday(zdt, currency)) {
      zdt = zdt.minusDays(1);
    }
    return zdt;
  }

  /**
   * Creates a new random, but reasonable, security.
   * 
   * @return the new security, or null if no security can be generated
   */
  public abstract T createSecurity();

}
