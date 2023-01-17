package com.main19.server.myplants.controller;

import com.main19.server.dto.SingleResponseDto;
import com.main19.server.myplants.dto.MyPlantsDto;
import com.main19.server.myplants.entity.MyPlants;
import com.main19.server.myplants.mapper.MyPlantsMapper;
import com.main19.server.myplants.service.MyPlantsService;
import com.main19.server.s3service.S3StorageService;
import javax.validation.Valid;
import javax.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/myplants")
public class MyPlantsController {

    private final MyPlantsMapper myPlantsMapper;
    private final MyPlantsService myPlantsService;
    private final S3StorageService storageService;

    @PostMapping
    public ResponseEntity postMyPlants(@RequestHeader(name = "Authorization") String token,
        @Valid @RequestBody MyPlantsDto.Post requestBody) {

        MyPlants myPlants = myPlantsMapper.myPlantsPostDtoToMyPlants(requestBody);

        MyPlants createdMyPlants = myPlantsService.createMyPlants(myPlants, requestBody.getMemberId(), token);

        MyPlantsDto.Response response = myPlantsMapper.myPlantsToMyPlantsResponseDto(createdMyPlants);

        return new ResponseEntity(new SingleResponseDto<>(response), HttpStatus.CREATED);
    }

    @PatchMapping("/{myplants-id}")
    public ResponseEntity patchMyPlants(@RequestHeader(name = "Authorization") String token, @PathVariable("myplants-id") @Positive long myPlantsId,
        @Valid @RequestBody MyPlantsDto.Patch requestBody) {

        MyPlants myPlants = myPlantsService.changeMyPlants(myPlantsId, requestBody.getGalleryId(),
            requestBody.getChangeNumber(), token);

        MyPlantsDto.Response response = myPlantsMapper.myPlantsToMyPlantsResponseDto(myPlants);

        return new ResponseEntity(new SingleResponseDto<>(response),HttpStatus.OK);
    }

    @GetMapping("/{myplants-id}")
    public ResponseEntity getMyPlants(@PathVariable("myplants-id") @Positive long myPlantsId) {

        MyPlants myPlants = myPlantsService.findMyPlants(myPlantsId);
        MyPlantsDto.Response response = myPlantsMapper.myPlantsToMyPlantsResponseDto(myPlants);

        return new ResponseEntity(new SingleResponseDto<>(response),HttpStatus.OK);
    }

    @DeleteMapping("/{myplants-id}")
    public ResponseEntity deleteMyPlants(@RequestHeader(name = "Authorization") String token, @PathVariable("myplants-id") @Positive long myPlantsId) {

        storageService.removeAllGalleryImage(myPlantsId,token);
        myPlantsService.deleteMyPlants(myPlantsId,token);

        return ResponseEntity.noContent().build();
    }
}
