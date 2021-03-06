package com.mingvalant.controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.VndErrors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.mingvalant.model.InventoryRepository;
import com.mingvalant.model.Item;
import com.mingvalant.services.Notificator;

import org.apache.log4j.Logger;

@RestController
@RequestMapping("/")
public class ValantController {
	static Logger logger = Logger.getLogger(ValantController.class.getName());
	private InventoryRepository inventoryRepository;
	private Notificator notificater;

	@Autowired
	ValantController(InventoryRepository inventoryRepository, Notificator notificater) {
		this.inventoryRepository = inventoryRepository;
		this.notificater = notificater;
	}
	
		
	@RequestMapping(method = RequestMethod.GET)
	Collection<Item> allItems() {
		Collection<Item> ret = new ArrayList<Item>();
		Iterable<Item> iter = this.inventoryRepository.findAll();
		for (Item item : iter) {
			ret.add(item);
		}
		return ret;
	}
	
	/**
	 * TODO: Is this a desirable behavior?
	 * Check if inventory repository contains the label of item for save. 
	 *   if not, we save the item and return HTTP status CREATED.
	 *   if yes, we ignore the item and return HTTP status UNPROCESSABLE_ENTITY.
	 * 
	 * to test:
	 *   curl -H "Content-Type: application/json" -X POST -d '{"label":"xyz","expiration":234567,"type":"A"}' http://localhost:8080/
	 */
	@RequestMapping(method = RequestMethod.POST)
	ResponseEntity<?> add(@RequestBody Item input) {
		Optional<Item> optional = this.inventoryRepository.findByLabelIgnoreCase(input.getLabel());
		Item result= null;
		HttpStatus httpStatus = null;
		
		if (optional.isPresent()) {
			result = optional.get();
			logger.info(String.format("Item already exist. Ignored the item for saving.", input.toString()));
			httpStatus = HttpStatus.UNPROCESSABLE_ENTITY;		// or HttpStatus.CONFLICT; ?
		} else {
			result = this.inventoryRepository.save(input);
			logger.info("Added item: "+ input.toString() );
			httpStatus = HttpStatus.CREATED;
		}
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setLocation(ServletUriComponentsBuilder
				.fromCurrentRequest().path("/")
				.buildAndExpand(result.getId()).toUri());
		return new ResponseEntity<>(null, httpHeaders, httpStatus);
	}
	/*
	 * curl -i -H "Accept: application/json" -X DELETE http://localhost:8080/Label1 
	 */
	@RequestMapping(value = "/{itemLabel}", method = RequestMethod.DELETE)
	ResponseEntity<?> delete(@PathVariable String itemLabel) {
		Item item = this.inventoryRepository.findByLabelIgnoreCase(itemLabel).orElseThrow(()->new LabelNotFoundException(itemLabel));
		this.inventoryRepository.delete(item);
		logger.info("Deleted item: "+ item.toString() );
		try {
			notificater.publishItemRemoved(item);
		} catch (InterruptedException e) {
			throw new NotificationFailedException("item removed");
		}
		HttpHeaders httpHeaders = new HttpHeaders();
		return new ResponseEntity<>(null, httpHeaders, HttpStatus.NO_CONTENT);
	}
}

@ControllerAdvice
class ValantControllerAdvice {
    @ResponseBody
    @ExceptionHandler( LabelNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    VndErrors labelNotFoundExceptionHandler(LabelNotFoundException ex) {
        return new VndErrors("error", ex.getMessage());
    }
    @ResponseBody
    @ExceptionHandler( InterruptedException.class)
    @ResponseStatus(HttpStatus.PARTIAL_CONTENT)
    VndErrors serviceInterruptExceptionHandler(NotificationFailedException ex) {
        return new VndErrors("error", ex.getMessage());
    }

}
class NotificationFailedException extends RuntimeException {
	public NotificationFailedException(String action) {
		super("notification of '" + action + "'event failed.");
	}
}

class LabelNotFoundException extends RuntimeException {
	public LabelNotFoundException(String label) {
		super("could not find label '" + label + "'.");
	}
}
