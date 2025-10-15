package com.MyridiusUAF.utils.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface TestType {
    Kind value();
    enum Kind { SYSTEM, E2E }
}
