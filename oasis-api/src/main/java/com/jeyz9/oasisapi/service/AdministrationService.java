package com.jeyz9.oasisapi.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.jeyz9.oasisapi.dto.AdministrationDto.Commission;
import com.jeyz9.oasisapi.dto.AdministrationDto.Policy;
import com.jeyz9.oasisapi.dto.AdministrationDto.Room;
import com.jeyz9.oasisapi.dto.AdministrationDto.RoomInput;
import com.jeyz9.oasisapi.dto.AdministrationDto.User;
import com.jeyz9.oasisapi.dto.AdministrationDto.UserInput;

public interface AdministrationService {
    List<User> users(@Min(0) int offset,@Min(1) @Max(100) int limit);
    void user(UUID id,@Valid UserInput input);
    List<Room> rooms();
    Room room(UUID id,@Valid RoomInput input);
    Map<String,Integer> policy();
    void policy(@Valid Policy input);
    void commission(UUID trainer,@Valid Commission input);
    List<Map<String,Object>> audit(@Min(0) int offset,@Min(1) @Max(100) int limit);
}
