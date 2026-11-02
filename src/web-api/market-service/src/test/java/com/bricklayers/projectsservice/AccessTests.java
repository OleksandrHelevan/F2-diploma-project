package com.bricklayers.projectsservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
public class AccessTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    private MockHttpServletResponse call(String url) throws Exception {
        return this.mockMvc.perform(get(url)).andReturn().getResponse();
    }

    private void assertOk(String url, String expectedBody) throws Exception {
        MockHttpServletResponse response = call(url);
        assertEquals(200, response.getStatus());
        assertEquals(expectedBody, response.getContentAsString());
    }

    private void assertForbidden(String url) throws Exception {
        MockHttpServletResponse response = call(url);
        assertEquals(403, response.getStatus());
        assertEquals("Forbidden", response.getErrorMessage());
    }

    @Test
    @DisplayName("Get for admin")
    @WithMockUser(username = "admin", roles = {"SUPERADMIN"})
    void getForAdmin() throws Exception {
        assertOk("/api/v1/roles/superadmin", "Superadmin");
    }

    @Test
    @DisplayName("Get for foreman")
    @WithMockUser(username = "foreman", roles = {"FOREMAN"})
    void getForForeman() throws Exception {
        assertOk("/api/v1/roles/foreman", "Foreman");
    }

    @Test
    @DisplayName("Get for foreman - admin")
    @WithMockUser(username = "admin", roles = {"SUPERADMIN"})
    void getForForemanAsAdmin() throws Exception {
        assertOk("/api/v1/roles/foreman", "Foreman");
    }

    @Test
    @DisplayName("Get for builder")
    @WithMockUser(username = "builder", roles = {"BUILDER"})
    void getForBuilder() throws Exception {
        assertOk("/api/v1/roles/builder", "Builder");
    }

    @Test
    @DisplayName("Get for builder - foreman")
    @WithMockUser(username = "foreman", roles = {"FOREMAN"})
    void getForBuilderAsForeman() throws Exception {
        assertOk("/api/v1/roles/builder", "Builder");
    }

    @Test
    @DisplayName("Get for builder - admin")
    @WithMockUser(username = "admin", roles = {"SUPERADMIN"})
    void getForBuilderAsAdmin() throws Exception {
        assertOk("/api/v1/roles/builder", "Builder");
    }

    @Test
    @DisplayName("Get for customer")
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    void getForCustomer() throws Exception {
        assertOk("/api/v1/roles/customer", "Customer");
    }

    @Test
    @DisplayName("Get for supplier")
    @WithMockUser(username = "supplier", roles = {"SUPPLIER"})
    void getForSupplier() throws Exception {
        assertOk("/api/v1/roles/supplier", "Supplier");
    }

    @Test
    @DisplayName("Get for all - admin")
    @WithMockUser(username = "admin", roles = {"SUPERADMIN"})
    void getForAllAsAdmin() throws Exception {
        assertOk("/api/v1/roles/all", "All");
    }

    @Test
    @DisplayName("Get for all - foreman")
    @WithMockUser(username = "foreman", roles = {"FOREMAN"})
    void getForAllAsForeman() throws Exception {
        assertOk("/api/v1/roles/all", "All");
    }

    @Test
    @DisplayName("Get for all - builder")
    @WithMockUser(username = "builder", roles = {"BUILDER"})
    void getForAllAsBuilder() throws Exception {
        assertOk("/api/v1/roles/all", "All");
    }

    @Test
    @DisplayName("Get for all - customer")
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    void getForAllAsCustomer() throws Exception {
        assertOk("/api/v1/roles/all", "All");
    }

    @Test
    @DisplayName("Get for all - supplier")
    @WithMockUser(username = "supplier", roles = {"SUPPLIER"})
    void getForAllAsSupplier() throws Exception {
        assertOk("/api/v1/roles/all", "All");
    }

    @Test
    @DisplayName("Get for admin - foreman (403 - expected)")
    @WithMockUser(username = "foreman", roles = {"FOREMAN"})
    void getForAdminAsForemanForbidden() throws Exception {
        assertForbidden("/api/v1/roles/superadmin");
    }

    @Test
    @DisplayName("Get for admin - builder (403 - expected)")
    @WithMockUser(username = "builder", roles = {"BUILDER"})
    void getForAdminAsBuilderForbidden() throws Exception {
        assertForbidden("/api/v1/roles/superadmin");
    }

    @Test
    @DisplayName("Get for admin - customer (403 - expected)")
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    void getForAdminAsCustomerForbidden() throws Exception {
        assertForbidden("/api/v1/roles/superadmin");
    }

    @Test
    @DisplayName("Get for admin - supplier (403 - expected)")
    @WithMockUser(username = "supplier", roles = {"SUPPLIER"})
    void getForAdminAsSupplierForbidden() throws Exception {
        assertForbidden("/api/v1/roles/superadmin");
    }

    @Test
    @DisplayName("Get for foreman - builder (403 - expected)")
    @WithMockUser(username = "builder", roles = {"BUILDER"})
    void getForForemanAsBuilderForbidden() throws Exception {
        assertForbidden("/api/v1/roles/foreman");
    }

    @Test
    @DisplayName("Get for foreman - supplier (403 - expected)")
    @WithMockUser(username = "supplier", roles = {"SUPPLIER"})
    void getForForemanAsSupplierForbidden() throws Exception {
        assertForbidden("/api/v1/roles/foreman");
    }

    @Test
    @DisplayName("Get for foreman - customer (403 - expected)")
    @WithMockUser(username = "customer", roles = {"CUSTOMER"})
    void getForForemanAsCustomerForbidden() throws Exception {
        assertForbidden("/api/v1/roles/foreman");
    }
}