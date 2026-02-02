package com.carselling.oldcar.graphql.dataloader;

import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.UserRepository;
import org.springframework.graphql.execution.BatchLoaderRegistry;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserDataLoader {

    public UserDataLoader(UserRepository userRepository, BatchLoaderRegistry registry) {
        registry.forTypePair(Long.class, User.class).registerMappedBatchLoader((keys, env) -> {
            Set<Long> userIds = keys;
            return Mono.fromSupplier(() -> {
                List<User> users = userRepository.findAllById(userIds);
                return users.stream().collect(Collectors.toMap(User::getId, user -> user));
            });
        });
    }
}
