package ru.practicum.event.service;

import ru.practicum.event.dto.*;
import ru.practicum.event.model.Event;
import ru.practicum.event.utill.EventGetAdminParam;
import ru.practicum.event.utill.EventGetPublicParam;
import ru.practicum.request.dto.EventRequestStatusUpdateRequest;
import ru.practicum.request.dto.EventRequestStatusUpdateResult;
import ru.practicum.request.dto.ParticipationRequestDto;

import java.util.List;

public interface EventService {

    List<ParticipationRequestDto> getRequests(long userId, long eventId);

    EventFullDto get(long userId, long eventId);

    List<EventShortDto> getAll(long userId, int from, int size);

    EventFullDto create(long userId, NewEventDto eventDto);

    EventFullDto update(long userId, long eventId, UpdateEventUserRequest updateEvent);

    EventRequestStatusUpdateResult updateRequestStatus(long userId, long eventId,
                                                       EventRequestStatusUpdateRequest eventRequestStatus,
                                                       Integer participantLimit, Boolean requestModeration);

    List<EventFullDto> getEventsByAdmin(EventGetAdminParam param);

    EventFullDto updateEventByAdmin(long eventId, UpdateEventAdminRequest updateEventAdminRequest);

    List<EventShortDto> getEventsByPublic(EventGetPublicParam param);

    EventFullDto getEventByPublic(long eventId);

    Event getEventById(long eventId);

    Event getEventByIdAndCheckOwner(long userId, long eventId);
}