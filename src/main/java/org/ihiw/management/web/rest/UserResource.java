package org.ihiw.management.web.rest;

import org.ihiw.management.config.Constants;
import org.ihiw.management.domain.Authority;
import org.ihiw.management.domain.IhiwUser;
import org.ihiw.management.domain.User;
import org.ihiw.management.repository.IhiwUserRepository;
import org.ihiw.management.repository.UserRepository;
import org.ihiw.management.security.AuthoritiesConstants;
import org.ihiw.management.service.MailService;
import org.ihiw.management.service.UserService;
import org.ihiw.management.service.dto.UserDTO;
import org.ihiw.management.web.rest.errors.BadRequestAlertException;
import org.ihiw.management.web.rest.errors.EmailAlreadyUsedException;
import org.ihiw.management.web.rest.errors.LoginAlreadyUsedException;

import io.github.jhipster.web.util.HeaderUtil;
import io.github.jhipster.web.util.PaginationUtil;
import io.github.jhipster.web.util.ResponseUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import javax.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static org.ihiw.management.security.AuthoritiesConstants.ADMIN;
import static org.ihiw.management.security.AuthoritiesConstants.PI;

/**
 * REST controller for managing users.
 * <p>
 * This class accesses the {@link User} entity, and needs to fetch its collection of authorities.
 * <p>
 * For a normal use-case, it would be better to have an eager relationship between User and Authority,
 * and send everything to the client side: there would be no View Model and DTO, a lot less code, and an outer-join
 * which would be good for performance.
 * <p>
 * We use a View Model and a DTO for 3 reasons:
 * <ul>
 * <li>We want to keep a lazy association between the user and the authorities, because people will
 * quite often do relationships with the user, and we don't want them to get the authorities all
 * the time for nothing (for performance reasons). This is the #1 goal: we should not impact our users'
 * application because of this use-case.</li>
 * <li> Not having an outer join causes n+1 requests to the database. This is not a real issue as
 * we have by default a second-level cache. This means on the first HTTP call we do the n+1 requests,
 * but then all authorities come from the cache, so in fact it's much better than doing an outer join
 * (which will get lots of data from the database, for each HTTP call).</li>
 * <li> As this manages users, for security reasons, we'd rather have a DTO layer.</li>
 * </ul>
 * <p>
 * Another option would be to have a specific JPA entity graph to handle this case.
 */
@RestController
@RequestMapping("/api")
public class UserResource {

    private final Logger log = LoggerFactory.getLogger(UserResource.class);

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final UserService userService;

    private final UserRepository userRepository;

    private final IhiwUserRepository ihiwUserRepository;

    private final MailService mailService;

    public UserResource(UserService userService, UserRepository userRepository, MailService mailService, IhiwUserRepository ihiwUserRepository) {

        this.userService = userService;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.ihiwUserRepository = ihiwUserRepository;
    }

    /**
     * {@code POST  /users}  : Creates a new user.
     * <p>
     * Creates a new user if the login and email are not already used, and sends an
     * mail with an activation link.
     * The user needs to be activated on creation.
     *
     * @param userDTO the user to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new user, or with status {@code 400 (Bad Request)} if the login or email is already in use.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     * @throws BadRequestAlertException {@code 400 (Bad Request)} if the login or email is already in use.
     */
    @PostMapping("/users")
    @PreAuthorize("hasRole(\"" + AuthoritiesConstants.ADMIN + "\")")
    public ResponseEntity<User> createUser(@Valid @RequestBody UserDTO userDTO) throws URISyntaxException {
        log.debug("REST request to save User : {}", userDTO);

        if (userDTO.getId() != null) {
            throw new BadRequestAlertException("A new user cannot already have an ID", "userManagement", "idexists");
            // Lowercase the user login before comparing with database
        } else if (userRepository.findOneByLogin(userDTO.getLogin().toLowerCase()).isPresent()) {
            throw new LoginAlreadyUsedException();
        } else if (userRepository.findOneByEmailIgnoreCase(userDTO.getEmail()).isPresent()) {
            throw new EmailAlreadyUsedException();
        } else {
            User newUser = userService.createUser(userDTO);
            mailService.sendCreationEmail(newUser);
            return ResponseEntity.created(new URI("/api/users/" + newUser.getLogin()))
                .headers(HeaderUtil.createAlert(applicationName,  "userManagement.created", newUser.getLogin()))
                .body(newUser);
        }
    }

    /**
     * {@code PUT /users} : Updates an existing User.
     *
     * @param userDTO the user to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the updated user.
     * @throws EmailAlreadyUsedException {@code 400 (Bad Request)} if the email is already in use.
     * @throws LoginAlreadyUsedException {@code 400 (Bad Request)} if the login is already in use.
     */
    @PutMapping("/users")
    public ResponseEntity<UserDTO> updateUser(@Valid @RequestBody UserDTO userDTO) {
        log.debug("REST request to update User : {}", userDTO);
        Optional<User> currentUser = userService.getUserWithAuthorities();
        IhiwUser currentIhiwUser = ihiwUserRepository.findByUserIsCurrentUser();
        Optional<User> existingUser = userService.getUserWithAuthorities(userDTO.getId());
        IhiwUser updateIhiwUser = ihiwUserRepository.findByUser(existingUser.get());

        //only admins and PIs can update users, but PIs only the users of their own lab
        if (currentUser.get().getAuthorities().contains(new Authority(ADMIN)) ||
            (currentUser.get().getAuthorities().contains(new Authority(PI)) && currentIhiwUser.getLab().equals(updateIhiwUser.getLab()))) {

            if (existingUser.isPresent() && (!existingUser.get().getId().equals(userDTO.getId()))) {
                throw new EmailAlreadyUsedException();
            }
            if (existingUser.isPresent() && (!existingUser.get().getId().equals(userDTO.getId()))) {
                throw new LoginAlreadyUsedException();
            }

            //only admins can change the role
            if (!currentUser.get().getAuthorities().contains(new Authority(ADMIN))){
                userDTO.setAuthorities(new HashSet<>());
                for (Authority auth : existingUser.get().getAuthorities()){
                    userDTO.getAuthorities().add(auth.getName());
                }
            }

            Optional<UserDTO> updatedUser = userService.updateUser(userDTO);

            return ResponseUtil.wrapOrNotFound(updatedUser,
                HeaderUtil.createAlert(applicationName, "userManagement.updated", userDTO.getLogin()));
        }
        return ResponseEntity.badRequest().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, "USER", userDTO.getLogin())).build();
    }

    /**
     * {@code GET /users} : get all users.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body all users.
     */
    @GetMapping("/users")
    public ResponseEntity<List<UserDTO>> getAllUsers(@RequestParam(value = "page" , required = false) Integer offset,
    @RequestParam(value = "size", required = false) Integer size, @RequestParam(value = "sort", required = false) String sort) {
        Optional<User> currentUser = userService.getUserWithAuthorities();
        Page<UserDTO> page = null;
        Pageable pageable;
        if(offset != null && size != null) {
            Sort sorting  = Sort.by(Sort.Direction.fromString(sort.split(",")[1]), sort.split(",")[0]);
            pageable = PageRequest.of(offset, size, sorting);
        } else {
            pageable = Pageable.unpaged();
        }
        if (currentUser.get().getAuthorities().contains(new Authority(ADMIN))) {
            page = userService.getAllManagedUsers(pageable);
        } else if (currentUser.get().getAuthorities().contains(new Authority(PI))) {
            IhiwUser currentIhiwUser = ihiwUserRepository.findByUserIsCurrentUser();
            page = userService.getMyManagedUsers(pageable, currentIhiwUser.getLab());
        }
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * Gets a list of all roles.
     * @return a string list of all roles.
     */
    @GetMapping("/users/authorities")
    public List<String> getAuthorities() {
        return userService.getAuthorities();
    }

    /**
     * {@code GET /users/:login} : get the "login" user.
     *
     * @param login the login of the user to find.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the "login" user, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/users/{login:" + Constants.LOGIN_REGEX + "}")
    public ResponseEntity<UserDTO> getUser(@PathVariable String login) {
        log.debug("REST request to get User : {}", login);
        return ResponseUtil.wrapOrNotFound(
            userService.getUserWithAuthoritiesByLogin(login)
                .map(UserDTO::new));
    }

    /**
     * {@code DELETE /users/:login} : delete the "login" User.
     *
     * @param login the login of the user to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/users/{login:" + Constants.LOGIN_REGEX + "}")
    @PreAuthorize("hasRole('" + AuthoritiesConstants.ADMIN + "')")
    public ResponseEntity<Void> deleteUser(@PathVariable String login) {
        log.debug("REST request to delete User: {}", login);
        userService.deleteUser(login);
        return ResponseEntity.noContent().headers(HeaderUtil.createAlert(applicationName,  "userManagement.deleted", login)).build();
    }
}
