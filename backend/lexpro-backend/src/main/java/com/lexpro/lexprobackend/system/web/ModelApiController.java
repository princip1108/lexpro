package com.lexpro.lexprobackend.system.web;

import com.lexpro.lexprobackend.system.service.ModelConfigurationService;
import com.lexpro.lexprobackend.system.web.dto.SaveModelConfigurationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/system/model-configurations")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class ModelApiController {
    private final ModelConfigurationService service;
    public ModelApiController(ModelConfigurationService service){this.service=service;}
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ModelConfigurationService.ModelView create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody SaveModelConfigurationRequest request){return service.save(Long.parseLong(jwt.getSubject()),null,request);}
    @PutMapping("/{id}")
    public ModelConfigurationService.ModelView update(@AuthenticationPrincipal Jwt jwt,@PathVariable long id,@Valid @RequestBody SaveModelConfigurationRequest request){return service.save(Long.parseLong(jwt.getSubject()),id,request);}
    @PostMapping("/{id}/activation")
    public ModelConfigurationService.ModelView activate(@AuthenticationPrincipal Jwt jwt,@PathVariable long id){return service.activate(Long.parseLong(jwt.getSubject()),id);}
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable long id){service.delete(Long.parseLong(jwt.getSubject()),id);}
}
