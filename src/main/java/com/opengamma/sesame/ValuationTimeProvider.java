/**
 * Copyright (C) 2013 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.sesame;

import org.threeten.bp.ZonedDateTime;

import com.opengamma.util.ArgumentChecker;

// TODO this will need to support thread local binding for full reval
// TODO cache invalidation. decorate or put it in here?
public class ValuationTimeProvider implements ValuationTimeProviderFunction {

  private ZonedDateTime _valuationTime;

  public ValuationTimeProvider() {
  }

  public ValuationTimeProvider(ZonedDateTime valuationTime) {
    _valuationTime = ArgumentChecker.notNull(valuationTime, "valuationTime");
  }

  public void setValuationTime(ZonedDateTime valuationTime) {
    _valuationTime = valuationTime;
  }

  @Override
  public ZonedDateTime get() {
    return _valuationTime;
  }
}
