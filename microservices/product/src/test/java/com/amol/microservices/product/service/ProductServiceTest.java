package com.amol.microservices.product.service;

import com.amol.microservices.product.entity.Product;
import com.amol.microservices.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    private final ProductRepository repository = mock(ProductRepository.class);
    private final ProductService service = new ProductService(repository);

    @Test
    void search_trimsBlanksToNullAndDelegates() {
        when(repository.search("phone", null)).thenReturn(List.of(new Product()));

        List<Product> result = service.search("  phone  ", "   ");

        assertEquals(1, result.size());
        verify(repository).search("phone", null);
    }

    @Test
    void search_byCategoryOnlyIsAllowed() {
        when(repository.search(null, "Electronics")).thenReturn(List.of());

        service.search(null, "Electronics");

        verify(repository).search(null, "Electronics");
    }

    @Test
    void search_requiresQueryOrCategory() {
        assertThrows(IllegalArgumentException.class, () -> service.search("   ", null));
        assertThrows(IllegalArgumentException.class, () -> service.search(null, null));
        verifyNoInteractions(repository);
    }

    @Test
    void search_rejectsOverlongQuery() {
        String tooLong = "x".repeat(ProductService.MAX_QUERY_LENGTH + 1);
        assertThrows(IllegalArgumentException.class, () -> service.search(tooLong, null));
        verifyNoInteractions(repository);
    }
}
