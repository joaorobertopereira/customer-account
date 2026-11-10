package br.com.helpcsistemas.customeraccount.shared.domain;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Catálogo canônico dos campos pessoais expostos por escopo de leitura. */
public final class PersonalDataFields {

    private PersonalDataFields() {}

    public static final Set<String> IDENTITY = Set.of(
            "cpf",
            "personalData.fullName",
            "personalData.socialName",
            "personalData.birthDate",
            "personalData.motherName",
            "personalData.maritalStatus",
            "personalData.nationality");
    public static final Set<String> CONTACT = Set.of("email", "mobilePhone");
    public static final Set<String> ADDRESS = Set.of("address");
    public static final Set<String> ALL =
            Stream.of(IDENTITY, CONTACT, ADDRESS).flatMap(Set::stream).collect(Collectors.toUnmodifiableSet());

    public static Set<String> forScopes(Set<String> scopes) {
        if (scopes.contains("customer:read") || scopes.contains("customer:read:full")) {
            return ALL;
        }
        return Stream.of(
                        scopes.contains("customer:read:identity") ? IDENTITY : Set.<String>of(),
                        scopes.contains("customer:read:contact") ? CONTACT : Set.<String>of(),
                        scopes.contains("customer:read:address") ? ADDRESS : Set.<String>of())
                .flatMap(Set::stream)
                .collect(Collectors.toUnmodifiableSet());
    }
}
