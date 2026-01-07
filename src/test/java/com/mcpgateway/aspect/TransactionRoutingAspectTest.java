package com.mcpgateway.aspect;

import com.mcpgateway.config.datasource.DataSourceContextHolder;
import com.mcpgateway.config.datasource.DataSourceType;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TransactionRoutingAspect
 *
 * Tests database read-write splitting routing logic
 */
@ExtendWith(MockitoExtension.class)
class TransactionRoutingAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    private TransactionRoutingAspect routingAspect;

    @BeforeEach
    void setUp() {
        routingAspect = new TransactionRoutingAspect();
    }

    @AfterEach
    void tearDown() {
        // Clean up ThreadLocal to prevent test pollution
        DataSourceContextHolder.clearDataSourceType();
    }

    @Test
    void routeDataSource_ReadOnlyTransaction_RoutesToReplica() throws Throwable {
        // Given
        Method method = TestService.class.getMethod("readOnlyMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getName()).thenReturn("readOnlyMethod");
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(joinPoint.proceed()).thenReturn("result");

        // When
        Object result = routingAspect.routeDataSource(joinPoint);

        // Then
        assertThat(result).isEqualTo("result");
        verify(joinPoint).proceed();

        // Context should be cleared after execution
        assertThat(DataSourceContextHolder.getDataSourceType()).isEqualTo(DataSourceType.MASTER); // default
    }

    @Test
    void routeDataSource_WriteTransaction_RoutesToMaster() throws Throwable {
        // Given
        Method method = TestService.class.getMethod("writeMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getName()).thenReturn("writeMethod");
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(joinPoint.proceed()).thenReturn("result");

        // When
        Object result = routingAspect.routeDataSource(joinPoint);

        // Then
        assertThat(result).isEqualTo("result");
        verify(joinPoint).proceed();

        // Context should be cleared after execution
        assertThat(DataSourceContextHolder.getDataSourceType()).isEqualTo(DataSourceType.MASTER);
    }

    @Test
    void routeDataSource_NoTransactionAnnotation_DefaultsToMaster() throws Throwable {
        // Given
        Method method = TestService.class.getMethod("noAnnotationMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getName()).thenReturn("noAnnotationMethod");
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(joinPoint.proceed()).thenReturn("result");

        // When
        Object result = routingAspect.routeDataSource(joinPoint);

        // Then
        assertThat(result).isEqualTo("result");
        verify(joinPoint).proceed();
    }

    @Test
    void routeDataSource_ClassLevelReadOnlyAnnotation_RoutesToReplica() throws Throwable {
        // Given
        Method method = ReadOnlyTestService.class.getMethod("someMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getName()).thenReturn("someMethod");
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(new ReadOnlyTestService());
        when(joinPoint.proceed()).thenReturn("result");

        // When
        Object result = routingAspect.routeDataSource(joinPoint);

        // Then
        assertThat(result).isEqualTo("result");
        verify(joinPoint).proceed();
    }

    @Test
    void routeDataSource_MethodOverridesClassAnnotation_UsesMethodAnnotation() throws Throwable {
        // Given
        Method method = ReadOnlyTestService.class.getMethod("writeMethodOverride");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getName()).thenReturn("writeMethodOverride");
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(new ReadOnlyTestService());
        when(joinPoint.proceed()).thenReturn("result");

        // When
        Object result = routingAspect.routeDataSource(joinPoint);

        // Then
        assertThat(result).isEqualTo("result");
        verify(joinPoint).proceed();
    }

    @Test
    void routeDataSource_ExceptionThrown_ContextStillCleared() throws Throwable {
        // Given
        Method method = TestService.class.getMethod("writeMethod");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getName()).thenReturn("writeMethod");
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(new TestService());
        when(joinPoint.proceed()).thenThrow(new RuntimeException("Database error"));

        // When & Then
        assertThatThrownBy(() -> routingAspect.routeDataSource(joinPoint))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database error");

        // Context should be cleared even after exception
        assertThat(DataSourceContextHolder.getDataSourceType()).isEqualTo(DataSourceType.MASTER);
    }

    @Test
    void routeDataSource_ErrorDeterminingTransactionType_DefaultsToMaster() throws Throwable {
        // Given - Mock a scenario where reflection fails
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getName()).thenReturn("someMethod");
        when(methodSignature.getMethod()).thenThrow(new NoSuchMethodException("Method not found"));
        when(joinPoint.proceed()).thenReturn("result");

        // When
        Object result = routingAspect.routeDataSource(joinPoint);

        // Then - Should default to master and continue
        assertThat(result).isEqualTo("result");
        verify(joinPoint).proceed();
    }

    // Test service classes for annotation testing

    public static class TestService {

        @Transactional(readOnly = true)
        public String readOnlyMethod() {
            return "read-only";
        }

        @Transactional(readOnly = false)
        public String writeMethod() {
            return "write";
        }

        @Transactional // Default is readOnly = false
        public String defaultTransactionMethod() {
            return "default";
        }

        public String noAnnotationMethod() {
            return "no-annotation";
        }
    }

    @Transactional(readOnly = true)
    public static class ReadOnlyTestService {

        public String someMethod() {
            return "inherited-read-only";
        }

        @Transactional(readOnly = false)
        public String writeMethodOverride() {
            return "override-write";
        }
    }
}
