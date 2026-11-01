package com.jeyz9.oasisapi.controller;
import com.jeyz9.oasisapi.dto.AdministrationDto;
import com.jeyz9.oasisapi.service.AdministrationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdministrationService admin;
    public AdminController(AdministrationService admin) {this.admin=admin;}
    @GetMapping("/users") public List<AdministrationDto.User> users(@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="25") int limit){return admin.users(offset,limit);}
    @PutMapping("/users/{id}/roles") @ResponseStatus(HttpStatus.NO_CONTENT) public void user(@PathVariable UUID id,@Valid @RequestBody AdministrationDto.UserInput input){admin.user(id,input);}
    @GetMapping("/rooms") public List<AdministrationDto.Room> rooms(){return admin.rooms();}
    @PostMapping("/rooms") @ResponseStatus(HttpStatus.CREATED) public AdministrationDto.Room room(@Valid @RequestBody AdministrationDto.RoomInput input){return admin.room(null,input);}
    @PutMapping("/rooms/{id}") public AdministrationDto.Room room(@PathVariable UUID id,@Valid @RequestBody AdministrationDto.RoomInput input){return admin.room(id,input);}
    @GetMapping("/business-policy") public Map<String,Integer> policy(){return admin.policy();}
    @PutMapping("/business-policy") @ResponseStatus(HttpStatus.NO_CONTENT) public void policy(@Valid @RequestBody AdministrationDto.Policy input){admin.policy(input);}
    @PutMapping("/trainers/{id}/commission") @ResponseStatus(HttpStatus.NO_CONTENT) public void commission(@PathVariable UUID id,@Valid @RequestBody AdministrationDto.Commission input){admin.commission(id,input);}
    @GetMapping("/audit-events") public List<Map<String,Object>> audit(@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="25") int limit){return admin.audit(offset,limit);}
}
