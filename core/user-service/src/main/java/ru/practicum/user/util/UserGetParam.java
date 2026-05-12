package ru.practicum.user.util;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Builder
@Getter
@Setter
public class UserGetParam {

    private List<Long> ids;

    @Builder.Default
    private Integer from = 0;

    @Builder.Default
    private Integer size = 10;
}
