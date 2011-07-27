/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.core.position;

import java.math.BigDecimal;

import com.opengamma.core.security.SecurityLink;
import com.opengamma.id.UniqueIdentifiable;
import com.opengamma.id.UniqueIdentifier;
import com.opengamma.util.PublicSPI;

/**
 * A position and a trade are related concepts and this interface provides common access.
 * <p>
 * A {@link Trade} stores details of an individual trade and refers to a quantity of a security.
 * A {@link Position} stores the combined set of trades forming a single position of a security.
 * Since both hold a quantity of a security, it can be useful to refer to both in a common way.
 * <p>
 * The reference to a security is held primarily by an identifier bundle.
 * However, this can become resolved, setting the security field with the full data.
 */
@PublicSPI
public interface PositionOrTrade extends UniqueIdentifiable {

  /**
   * Gets the unique identifier.
   * 
   * @return the unique identifier, not null
   */
  UniqueIdentifier getUniqueId();

  /**
   * Gets the amount of the position held in terms of the security.
   * 
   * @return the amount of the position
   */
  BigDecimal getQuantity();

  /**
   * Gets a link connecting to the security.
   * <p>
   * The link holds a strong or weak reference to the security
   * and can be resolved to the actual security when required.
   * 
   * @return the security link, not null
   */
  SecurityLink getSecurityLink();

}
