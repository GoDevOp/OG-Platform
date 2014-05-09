/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.provider.permission;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.joda.beans.Bean;
import org.joda.beans.BeanBuilder;
import org.joda.beans.BeanDefinition;
import org.joda.beans.JodaBeanUtils;
import org.joda.beans.MetaProperty;
import org.joda.beans.Property;
import org.joda.beans.PropertyDefinition;
import org.joda.beans.impl.direct.DirectBeanBuilder;
import org.joda.beans.impl.direct.DirectMetaBean;
import org.joda.beans.impl.direct.DirectMetaProperty;
import org.joda.beans.impl.direct.DirectMetaPropertyMap;

import com.opengamma.util.PublicSPI;

/**
 * Result of permission checks for a set of permissions for a user.
 * <p>
 * This class is mutable and not thread-safe.
 */
@PublicSPI
@BeanDefinition
public class PermissionCheckProviderResult implements Bean {

  /**
   * The permission check result.
   */
  @PropertyDefinition(validate = "notNull")
  private final Map<String, Boolean> _checkedPermissions = new HashMap<>();

  /**
   * Creates an instance.
   */
  public PermissionCheckProviderResult() {
  }

  /**
   * Creates an instance.
   * 
   * @param checkedPermissions  the map of checked permissions, not null
   */
  public PermissionCheckProviderResult(Map<String, Boolean> checkedPermissions) {
    setCheckedPermissions(checkedPermissions);
  }

  //------------------------- AUTOGENERATED START -------------------------
  ///CLOVER:OFF
  /**
   * The meta-bean for {@code PermissionCheckProviderResult}.
   * @return the meta-bean, not null
   */
  public static PermissionCheckProviderResult.Meta meta() {
    return PermissionCheckProviderResult.Meta.INSTANCE;
  }

  static {
    JodaBeanUtils.registerMetaBean(PermissionCheckProviderResult.Meta.INSTANCE);
  }

  @Override
  public PermissionCheckProviderResult.Meta metaBean() {
    return PermissionCheckProviderResult.Meta.INSTANCE;
  }

  @Override
  public <R> Property<R> property(String propertyName) {
    return metaBean().<R>metaProperty(propertyName).createProperty(this);
  }

  @Override
  public Set<String> propertyNames() {
    return metaBean().metaPropertyMap().keySet();
  }

  //-----------------------------------------------------------------------
  /**
   * Gets the permission check result.
   * @return the value of the property, not null
   */
  public Map<String, Boolean> getCheckedPermissions() {
    return _checkedPermissions;
  }

  /**
   * Sets the permission check result.
   * @param checkedPermissions  the new value of the property, not null
   */
  public void setCheckedPermissions(Map<String, Boolean> checkedPermissions) {
    JodaBeanUtils.notNull(checkedPermissions, "checkedPermissions");
    this._checkedPermissions.clear();
    this._checkedPermissions.putAll(checkedPermissions);
  }

  /**
   * Gets the the {@code checkedPermissions} property.
   * @return the property, not null
   */
  public final Property<Map<String, Boolean>> checkedPermissions() {
    return metaBean().checkedPermissions().createProperty(this);
  }

  //-----------------------------------------------------------------------
  @Override
  public PermissionCheckProviderResult clone() {
    return JodaBeanUtils.cloneAlways(this);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj != null && obj.getClass() == this.getClass()) {
      PermissionCheckProviderResult other = (PermissionCheckProviderResult) obj;
      return JodaBeanUtils.equal(getCheckedPermissions(), other.getCheckedPermissions());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int hash = getClass().hashCode();
    hash += hash * 31 + JodaBeanUtils.hashCode(getCheckedPermissions());
    return hash;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder(64);
    buf.append("PermissionCheckProviderResult{");
    int len = buf.length();
    toString(buf);
    if (buf.length() > len) {
      buf.setLength(buf.length() - 2);
    }
    buf.append('}');
    return buf.toString();
  }

  protected void toString(StringBuilder buf) {
    buf.append("checkedPermissions").append('=').append(JodaBeanUtils.toString(getCheckedPermissions())).append(',').append(' ');
  }

  //-----------------------------------------------------------------------
  /**
   * The meta-bean for {@code PermissionCheckProviderResult}.
   */
  public static class Meta extends DirectMetaBean {
    /**
     * The singleton instance of the meta-bean.
     */
    static final Meta INSTANCE = new Meta();

    /**
     * The meta-property for the {@code checkedPermissions} property.
     */
    @SuppressWarnings({"unchecked", "rawtypes" })
    private final MetaProperty<Map<String, Boolean>> _checkedPermissions = DirectMetaProperty.ofReadWrite(
        this, "checkedPermissions", PermissionCheckProviderResult.class, (Class) Map.class);
    /**
     * The meta-properties.
     */
    private final Map<String, MetaProperty<?>> _metaPropertyMap$ = new DirectMetaPropertyMap(
        this, null,
        "checkedPermissions");

    /**
     * Restricted constructor.
     */
    protected Meta() {
    }

    @Override
    protected MetaProperty<?> metaPropertyGet(String propertyName) {
      switch (propertyName.hashCode()) {
        case -1315352995:  // checkedPermissions
          return _checkedPermissions;
      }
      return super.metaPropertyGet(propertyName);
    }

    @Override
    public BeanBuilder<? extends PermissionCheckProviderResult> builder() {
      return new DirectBeanBuilder<PermissionCheckProviderResult>(new PermissionCheckProviderResult());
    }

    @Override
    public Class<? extends PermissionCheckProviderResult> beanType() {
      return PermissionCheckProviderResult.class;
    }

    @Override
    public Map<String, MetaProperty<?>> metaPropertyMap() {
      return _metaPropertyMap$;
    }

    //-----------------------------------------------------------------------
    /**
     * The meta-property for the {@code checkedPermissions} property.
     * @return the meta-property, not null
     */
    public final MetaProperty<Map<String, Boolean>> checkedPermissions() {
      return _checkedPermissions;
    }

    //-----------------------------------------------------------------------
    @Override
    protected Object propertyGet(Bean bean, String propertyName, boolean quiet) {
      switch (propertyName.hashCode()) {
        case -1315352995:  // checkedPermissions
          return ((PermissionCheckProviderResult) bean).getCheckedPermissions();
      }
      return super.propertyGet(bean, propertyName, quiet);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void propertySet(Bean bean, String propertyName, Object newValue, boolean quiet) {
      switch (propertyName.hashCode()) {
        case -1315352995:  // checkedPermissions
          ((PermissionCheckProviderResult) bean).setCheckedPermissions((Map<String, Boolean>) newValue);
          return;
      }
      super.propertySet(bean, propertyName, newValue, quiet);
    }

    @Override
    protected void validate(Bean bean) {
      JodaBeanUtils.notNull(((PermissionCheckProviderResult) bean)._checkedPermissions, "checkedPermissions");
    }

  }

  ///CLOVER:ON
  //-------------------------- AUTOGENERATED END --------------------------
}