/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.api.factory.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonSyntaxException;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.response.Response;

import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.model.project.ProjectConfig;
import org.eclipse.che.api.core.model.user.User;
import org.eclipse.che.api.core.model.workspace.WorkspaceConfig;
import org.eclipse.che.api.core.rest.ApiExceptionMapper;
import org.eclipse.che.api.core.rest.shared.dto.ServiceError;
import org.eclipse.che.api.factory.server.FactoryService.FactoryParametersResolverHolder;
import org.eclipse.che.api.factory.server.builder.FactoryBuilder;
import org.eclipse.che.api.factory.server.impl.SourceStorageParametersValidator;
import org.eclipse.che.api.factory.server.model.impl.AuthorImpl;
import org.eclipse.che.api.factory.server.model.impl.FactoryImpl;
import org.eclipse.che.api.factory.shared.dto.FactoryDto;
import org.eclipse.che.api.core.model.factory.Factory;
import org.eclipse.che.api.machine.server.model.impl.MachineConfigImpl;
import org.eclipse.che.api.machine.server.model.impl.MachineSourceImpl;
import org.eclipse.che.api.machine.server.model.impl.ServerConfImpl;
import org.eclipse.che.api.user.server.PreferenceManager;
import org.eclipse.che.api.user.server.UserManager;
import org.eclipse.che.api.user.server.model.impl.UserImpl;
import org.eclipse.che.api.workspace.server.WorkspaceManager;
import org.eclipse.che.api.workspace.server.model.impl.EnvironmentImpl;
import org.eclipse.che.api.workspace.server.model.impl.ProjectConfigImpl;
import org.eclipse.che.api.workspace.server.model.impl.SourceStorageImpl;
import org.eclipse.che.api.workspace.server.model.impl.WorkspaceConfigImpl;
import org.eclipse.che.api.workspace.shared.dto.EnvironmentDto;
import org.eclipse.che.commons.env.EnvironmentContext;
import org.eclipse.che.commons.json.JsonHelper;
import org.eclipse.che.commons.subject.SubjectImpl;
import org.eclipse.che.dto.server.DtoFactory;
import org.everrest.assured.EverrestJetty;
import org.everrest.core.Filter;
import org.everrest.core.GenericContainerRequest;
import org.everrest.core.RequestFilter;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.jayway.restassured.RestAssured.given;
import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.eclipse.che.api.factory.server.DtoConverter.asDto;
import static org.everrest.assured.JettyHttpServer.ADMIN_USER_NAME;
import static org.everrest.assured.JettyHttpServer.ADMIN_USER_PASSWORD;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anySetOf;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;


@Listeners(value = {EverrestJetty.class, MockitoTestNGListener.class})
public class FactoryServiceTest {

    private static final String SERVICE_PATH            = "/factory";
    private static final String FACTORY_ID              = "correctFactoryId";
    private static final String USER_ID                 = "userId";
    private static final String USER_EMAIL              = "email";
    private static final String WORKSPACE_NAME          = "workspace";
    private static final String PROJECT_SOURCE_TYPE     = "git";
    private static final String PROJECT_SOURCE_LOCATION = "http://github.com/codenvy/platform-api.git";
    private static final String FACTORY_IMAGE_MIME_TYPE = "image/jpeg";
    private static final String IMAGE_NAME              = "image12";


    private static final DtoFactory DTO = DtoFactory.getInstance();

    private final String SERVICE_PATH_RESOLVER = SERVICE_PATH + "/resolver";

    @Mock
    private FactoryManager                  factoryManager;
    @Mock
    private FactoryCreateValidator          createValidator;
    @Mock
    private FactoryAcceptValidator          acceptValidator;
    @Mock
    private PreferenceManager               preferenceManager;
    @Mock
    private UserManager                     userManager;
    @Mock
    private FactoryEditValidator            editValidator;
    @Mock
    private WorkspaceManager                workspaceManager;
    @Mock
    private FactoryParametersResolverHolder factoryParametersResolverHolder;
    @Mock
    private Set<FactoryParametersResolver>  factoryParametersResolvers;
    @Mock
    private FactoryBuilder                  factoryBuilder;

    @InjectMocks
    private FactoryService factoryService;

    @SuppressWarnings("unused")
    private ApiExceptionMapper apiExceptionMapper;
    @SuppressWarnings("unused")
    private EnvironmentFilter  environmentFilter;

    private User user;

    @BeforeMethod
    public void setUp() throws Exception {
        final FactoryBuilder factoryBuilder = spy(new FactoryBuilder(new SourceStorageParametersValidator()));
        doNothing().when(factoryBuilder).checkValid(any(FactoryDto.class));
        when(factoryParametersResolverHolder.getFactoryParametersResolvers()).thenReturn(factoryParametersResolvers);
        user = new UserImpl(USER_ID, USER_EMAIL, ADMIN_USER_NAME);
        when(userManager.getById(anyString())).thenReturn(user);
        when(preferenceManager.find(USER_ID)).thenReturn(ImmutableMap.of("preference", "value"));
    }

    @Filter
    public static class EnvironmentFilter implements RequestFilter {
        public void doFilter(GenericContainerRequest request) {
            EnvironmentContext context = EnvironmentContext.getCurrent();
            context.setSubject(new SubjectImpl(ADMIN_USER_NAME, USER_ID, ADMIN_USER_PASSWORD, false));
        }
    }

    // FactoryService#saveFactory(Iterator<FileItem> formData) tests:

    @Test
    public void shouldSaveFactoryWithImagesFromFormData() throws Exception {
        final Factory factory = createFactory();
        final FactoryDto factoryDto = asDto(factory, user);
        when(factoryManager.saveFactory(any(FactoryDto.class), anySetOf(FactoryImage.class))).thenReturn(factory);
        when(factoryManager.getById(FACTORY_ID)).thenReturn(factory);
        when(factoryBuilder.build(any(InputStream.class))).thenReturn(factoryDto);

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .multiPart("factory", JsonHelper.toJson(factoryDto), APPLICATION_JSON)
                                         .multiPart("image", getImagePath().toFile(), FACTORY_IMAGE_MIME_TYPE)
                                         .expect()
                                         .statusCode(200)
                                         .when()
                                         .post(SERVICE_PATH);


        final FactoryDto result = getFromResponse(response, FactoryDto.class);
        final boolean found = result.getLinks()
                                    .stream()
                                    .anyMatch(link -> link.getRel().equals("image")
                                                      && link.getProduces().equals(FACTORY_IMAGE_MIME_TYPE)
                                                      && !link.getHref().isEmpty());
        factoryDto.withLinks(result.getLinks())
                  .getCreator()
                  .withCreated(result.getCreator().getCreated());
        assertEquals(result, factoryDto);
        assertTrue(found);
    }

    @Test
    public void shouldSaveFactoryFromFormDataWithoutImages() throws Exception {
        final Factory factory = createFactory();
        final FactoryDto factoryDto = asDto(factory, user);
        when(factoryManager.saveFactory(any(FactoryDto.class), anySetOf(FactoryImage.class))).thenReturn(factory);
        when(factoryBuilder.build(any(InputStream.class))).thenReturn(factoryDto);

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .multiPart("factory", JsonHelper.toJson(factoryDto), APPLICATION_JSON)
                                         .expect()
                                         .statusCode(200)
                                         .when()
                                         .post("/private" + SERVICE_PATH);
        final FactoryDto result = getFromResponse(response, FactoryDto.class);
        factoryDto.withLinks(result.getLinks())
                  .getCreator()
                  .withCreated(result.getCreator().getCreated());
        assertEquals(result, factoryDto);
    }

    @Test
    public void shouldSaveFactoryWithSetImageButWithOutImageContent() throws Exception {
        final Factory factory = createFactory();
        when(factoryManager.saveFactory(any(FactoryDto.class), anySetOf(FactoryImage.class))).thenReturn(factory);
        when(factoryManager.getById(FACTORY_ID)).thenReturn(factory);
        final FactoryDto factoryDto = asDto(factory, user);
        when(factoryBuilder.build(any(InputStream.class))).thenReturn(factoryDto);

        given().auth()
               .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
               .multiPart("factory", DTO.toJson(factoryDto), APPLICATION_JSON)
               .multiPart("image", File.createTempFile("img", ".jpeg"), "image/jpeg")
               .expect()
               .statusCode(200)
               .when()
               .post("/private" + SERVICE_PATH);

        verify(factoryManager).saveFactory(eq(factoryDto), eq(Collections.<FactoryImage>emptySet()));
    }

    @Test
    public void shouldThrowBadRequestExceptionWhenInvalidFactorySectionProvided() throws Exception {
        when(factoryBuilder.build(any(InputStream.class))).thenThrow(new JsonSyntaxException("Invalid json"));
        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .multiPart("factory", "invalid content", FACTORY_IMAGE_MIME_TYPE)
                                         .expect()
                                         .statusCode(400)
                                         .when()
                                         .post("/private" + SERVICE_PATH);

        final ServiceError err = getFromResponse(response, ServiceError.class);
        assertEquals(err.getMessage(), "Invalid JSON value of the field 'factory' provided");
    }

    @Test
    public void shouldThrowBadRequestExceptionWhenNoFactorySectionProvided() throws Exception {
        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .multiPart("some data", "some content", FACTORY_IMAGE_MIME_TYPE)
                                         .expect()
                                         .statusCode(400)
                                         .when()
                                         .post("/private" + SERVICE_PATH);

        final ServiceError err = getFromResponse(response, ServiceError.class);
        assertEquals(err.getMessage(), "factory configuration required");
    }

    @Test
    public void shouldThrowServerExceptionWhenImpossibleToBuildFactoryFromProvidedData() throws Exception {
        final String errMessage = "eof";
        when(factoryBuilder.build(any(InputStream.class))).thenThrow(new IOException(errMessage));
        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .multiPart("factory", "any content", FACTORY_IMAGE_MIME_TYPE)
                                         .expect()
                                         .statusCode(500)
                                         .when()
                                         .post("/private" + SERVICE_PATH);

        final ServiceError err = getFromResponse(response, ServiceError.class);
        assertEquals(err.getMessage(), errMessage);
    }

    // FactoryService#saveFactory(FactoryDto factoryDto) tests:

    @Test
    public void shouldSaveFactoryWithoutImages() throws Exception {
        final Factory factory = createFactory();
        final FactoryDto factoryDto = asDto(factory, user);
        when(factoryManager.saveFactory(any(FactoryDto.class))).thenReturn(factory);

        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType(ContentType.JSON)
                                         .body(factoryDto)
                                         .expect()
                                         .statusCode(200)
                                         .post(SERVICE_PATH);

        assertEquals(getFromResponse(response, FactoryDto.class).withLinks(emptyList()), factoryDto);
    }

    @Test
    public void shouldThrowBadRequestExceptionWhenFactoryConfigurationNotProvided() throws Exception {
        final Response response = given().auth()
                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                         .contentType(ContentType.JSON)
                                         .expect()
                                         .statusCode(400)
                                         .post(SERVICE_PATH);
        final String errMessage = getFromResponse(response, ServiceError.class).getMessage();
        assertEquals(errMessage, "Factory configuration required");
    }

    // FactoryService#getFactory(String factoryId, Boolean validate) tests:

    @Test
    public void shouldReturnFactoryByIdentifierWithoutValidation() throws Exception {
        final Factory factory = createFactory();
        final FactoryDto factoryDto = asDto(factory, user);
        when(factoryManager.getById(FACTORY_ID)).thenReturn(factory);
        when(factoryManager.getFactoryImages(FACTORY_ID)).thenReturn(emptySet());

        final Response response = given().when()
                                         .expect()
                                         .statusCode(200)
                                         .get(SERVICE_PATH + "/" + FACTORY_ID);

        assertEquals(getFromResponse(response, FactoryDto.class).withLinks(emptyList()), factoryDto);
    }

    @Test
    public void shouldReturnFactoryByIdentifierWithValidation() throws Exception {
        final Factory factory = createFactory();
        final FactoryDto factoryDto = asDto(factory, user);
        when(factoryManager.getById(FACTORY_ID)).thenReturn(factory);
        when(factoryManager.getFactoryImages(FACTORY_ID)).thenReturn(emptySet());
        doNothing().when(acceptValidator).validateOnAccept(any(FactoryDto.class));

        final Response response = given().when()
                                         .expect()
                                         .statusCode(200)
                                         .get(SERVICE_PATH + "/" + FACTORY_ID + "?validate=true");

        assertEquals(getFromResponse(response, FactoryDto.class).withLinks(emptyList()), factoryDto);
    }

    @Test
    public void shouldThrowNotFoundExceptionWhenFactoryIsNotExist() throws Exception {
        final String errMessage = format("Factory with id %s is not found", FACTORY_ID);
        doThrow(new NotFoundException(errMessage)).when(factoryManager)
                                                  .getById(anyString());

        final Response response = given().expect()
                                         .statusCode(404)
                                         .when()
                                         .get(SERVICE_PATH + "/" + FACTORY_ID);

        assertEquals(getFromResponse(response, ServiceError.class).getMessage(), errMessage);
    }


    // FactoryService#getFactoryByAttribute(Integer skipCount, Integer maxItems, UriInfo uriInfo) tests:


    // FactoryService#updateFactory(String factoryId, FactoryDto update) tests:


//    @Test
//    public void shouldBeAbleToUpdateFactory() throws Exception {
//        final Factory existed = createFactory();
//        final Factory update = createFactoryWithStorage("git", "http://github.com/codenvy/platform-api1.git");
//        when(factoryManager.getById(FACTORY_ID)).thenReturn(existed);
//
//        final Response response = given().auth()
//                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
//                                         .contentType(APPLICATION_JSON)
//                                         .body(JsonHelper.toJson(asDto(update)))
//                                         .when()
//                                         .expect()
//                                         .statusCode(200)
//                                         .put("/private" + SERVICE_PATH + "/" + FACTORY_ID);
//
//        final FactoryDto responseFactory = getFromResponse(response, FactoryDto.class);
//        assertNotEquals(responseFactory.withLinks(emptyList()), asDto(existed));
//        verify(factoryManager).updateFactory(eq(update), any());
//    }

//
//    /**
//     * Checks that the user can not update an unknown existing factory
//     */
//    @Test
//    public void shouldNotBeAbleToUpdateAnUnknownFactory() throws Exception {
//        // given
//        FactoryDto factory = createFactoryWithStorage1("git", "http://github.com/codenvy/platform-api.git");
//        doThrow(new NotFoundException(format("Factory with id %s is not found.", ILLEGAL_FACTORY_ID))).when(factoryManager)
//                                                                                                      .getById(anyString());
//
//        // when, then
//        Response response = given().auth().basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
//                                   .contentType(APPLICATION_JSON)
//                                   .body(JsonHelper.toJson(factory))
//                                   .when()
//                                   .put("/private" + SERVICE_PATH + "/" + ILLEGAL_FACTORY_ID);
//
//        assertEquals(response.getStatusCode(), 404);
//        assertEquals(DTO.createDtoFromJson(response.getBody().asString(), ServiceError.class).getMessage(),
//                     format("Factory with id %s is not found.", ILLEGAL_FACTORY_ID));
//    }
//
//    /**
//     * Checks that the user can not update a factory with a null one
//     */
//    @Test
//    public void shouldNotBeAbleToUpdateANullFactory() throws Exception {
//
//        // when, then
//        Response response = given().auth()
//                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
//                                   .contentType(APPLICATION_JSON)
//                                   .when()
//                                   .put("/private" + SERVICE_PATH + "/" + ILLEGAL_FACTORY_ID);
//
//        assertEquals(response.getStatusCode(), BAD_REQUEST.getStatusCode());
//        assertEquals(DTO.createDtoFromJson(response.getBody().asString(), ServiceError.class).getMessage(),
//                     "The updating factory shouldn't be null");
//
//    }

    // FactoryService#removeFactory(String id) tests:

    @Test
    public void shouldRemoveFactoryByGivenIdentifier() throws Exception {
        final Factory factory = createFactory();
        when(factoryManager.getById(FACTORY_ID)).thenReturn(factory);

        given().auth()
               .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
               .param("id", FACTORY_ID)
               .expect()
               .statusCode(204)
               .when()
               .delete("/private" + SERVICE_PATH + "/" + FACTORY_ID);

        verify(factoryManager).removeFactory(FACTORY_ID);
    }

    @Test
    public void shouldNotBeAbleToRemoveNotExistingFactory() throws Exception {
        doThrow(new NotFoundException("Not found")).when(factoryManager).removeFactory(anyString());

        Response response = given().auth()
                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
                                   .param("id", FACTORY_ID)
                                   .when()
                                   .delete("/private" + SERVICE_PATH + "/" + FACTORY_ID);

        assertEquals(response.getStatusCode(), 404);
    }

    // FactoryService#getImage(String factoryId, String imageId) tests:

    @Test
    public void shouldReturnFactoryImageWithGivenName() throws Exception {
        final byte[] imageContent = Files.readAllBytes(getImagePath());
        final FactoryImage image = new FactoryImage(imageContent, FACTORY_IMAGE_MIME_TYPE, IMAGE_NAME);
        when(factoryManager.getFactoryImages(FACTORY_ID, IMAGE_NAME)).thenReturn(ImmutableSet.of(image));

        final Response response = given().when()
                                         .expect()
                                         .statusCode(200)
                                         .get(SERVICE_PATH + "/" + FACTORY_ID + "/image?imgId=" + IMAGE_NAME);

        assertEquals(response.getContentType(), FACTORY_IMAGE_MIME_TYPE);
        assertEquals(response.getHeader("content-length"), String.valueOf(imageContent.length));
        assertEquals(response.asByteArray(), imageContent);
    }

    @Test
    public void shouldReturnFirstFoundFactoryImageWhenImageNameNotSpecified() throws Exception {
        final byte[] imageContent = Files.readAllBytes(getImagePath());
        final FactoryImage image = new FactoryImage(imageContent, FACTORY_IMAGE_MIME_TYPE, IMAGE_NAME);
        when(factoryManager.getFactoryImages(FACTORY_ID)).thenReturn(ImmutableSet.of(image));

        final Response response = given().when()
                                         .expect()
                                         .statusCode(200)
                                         .get(SERVICE_PATH + "/" + FACTORY_ID + "/image");

        assertEquals(response.getContentType(), "image/jpeg");
        assertEquals(response.getHeader("content-length"), String.valueOf(imageContent.length));
        assertEquals(response.asByteArray(), imageContent);
    }

    @Test
    public void shouldThrowNotFoundExceptionWhenFactoryImageWithGivenIdentifierIsNotExist() throws Exception {
        final String errMessage = "Image with name " + IMAGE_NAME + " is not found";
        when(factoryManager.getFactoryImages(FACTORY_ID, IMAGE_NAME)).thenThrow(new NotFoundException(errMessage));

        final Response response = given().expect()
                                         .statusCode(404)
                                         .when()
                                         .get(SERVICE_PATH + "/" + FACTORY_ID + "/image?imgId=" + IMAGE_NAME);

        assertEquals(getFromResponse(response, ServiceError.class).getMessage(), errMessage);
    }

    // FactoryService#getFactorySnippet(String factoryId, String type, UriInfo uriInfo) tests:
//
//    @Test
//    public void shouldBeAbleToReturnUrlSnippet(ITestContext context) throws Exception {
//        when(factoryManager.getById(FACTORY_ID)).thenReturn(DTO.createDto(FactoryDto.class));
//
//        given().expect()
//               .statusCode(200)
//               .contentType(MediaType.TEXT_PLAIN)
//               .body(equalTo(getServerUrl(context) + "/factory?id=" + FACTORY_ID))
//               .when()
//               .get(SERVICE_PATH + "/" + FACTORY_ID + "/snippet?type=url");
//    }
//
//    @Test
//    public void shouldBeAbleToReturnUrlSnippetIfTypeIsNotSet(ITestContext context) throws Exception {
//        // given
//        when(factoryManager.getById(FACTORY_ID)).thenReturn(DTO.createDto
//                (FactoryDto.class));
//
//        // when, then
//        given().expect()
//               .statusCode(200)
//               .contentType(MediaType.TEXT_PLAIN)
//               .body(equalTo(getServerUrl(context) + "/factory?id=" + FACTORY_ID))
//               .when()
//               .get(SERVICE_PATH + "/" + FACTORY_ID + "/snippet");
//    }
//
//    @Test
//    public void shouldBeAbleToReturnHtmlSnippet(ITestContext context) throws Exception {
//        // given
//        when(factoryManager.getById(FACTORY_ID)).thenReturn(DTO.createDto(FactoryDto.class));
//
//        // when, then
//        Response response = given().expect()
//                                   .statusCode(200)
//                                   .contentType(MediaType.TEXT_PLAIN)
//                                   .when()
//                                   .get(SERVICE_PATH + "/" + FACTORY_ID + "/snippet?type=html");
//
//        assertEquals(response.body().asString(), "<script type=\"text/javascript\" src=\"" + getServerUrl(context) +
//                                                 "/factory/resources/factory.js?" + FACTORY_ID + "\"></script>");
//    }
//
//    @Test
//    public void shouldBeAbleToReturnMarkdownSnippetForFactory1WithImage(ITestContext context) throws Exception {
//        // given
//        SourceStorageDto storageDto = DTO.createDto(SourceStorageDto.class)
//                                  .withType("git")
//                                  .withLocation("http://github.com/codenvy/platform-api.git");
//        FactoryDto factory = DTO.createDto(FactoryDto.class)
//                             .withV("4.0")
//                             .withWorkspace(DTO.createDto(WorkspaceConfigDto.class)
//                                               .withProjects(Collections.singletonList(DTO.createDto(ProjectConfigDto.class)
//                                                                                          .withSource(storageDto))))
//                             .withId(FACTORY_ID)
//                             .withButton(DTO.createDto(ButtonDto.class)
//                                            .withType(ButtonDto.ButtonType.logo));
//        String imageName = "1241234";
//        FactoryImage image = new FactoryImage();
//        image.setName(imageName);
//
//        when(factoryManager.getById(FACTORY_ID)).thenReturn(factory);
//        when(factoryManager.getFactoryImages(FACTORY_ID, null)).thenReturn(new HashSet<>(Collections.singletonList(image)));
//        // when, then
//        given().expect()
//               .statusCode(200)
//               .contentType(MediaType.TEXT_PLAIN)
//               .body(
//                       equalTo("[![alt](" + getServerUrl(context) + "/api/factory/" + FACTORY_ID + "/image?imgId=" +
//                               imageName + ")](" +
//                               getServerUrl(context) + "/factory?id=" +
//                               FACTORY_ID + ")")
//                    )
//               .when()
//               .get(SERVICE_PATH + "/" + FACTORY_ID + "/snippet?type=markdown");
//    }
//
//
//    @Test
//    public void shouldBeAbleToReturnMarkdownSnippetForFactory2WithImage(ITestContext context) throws Exception {
//        // given
//        String imageName = "1241234";
//        FactoryDto factory = DTO.createDto(FactoryDto.class);
//        factory.setId(FACTORY_ID);
//        factory.setV("2.0");
//        factory.setButton(DTO.createDto(ButtonDto.class).withType(ButtonDto.ButtonType.logo));
//
//        FactoryImage image = new FactoryImage();
//        image.setName(imageName);
//
//        when(factoryManager.getById(FACTORY_ID)).thenReturn(factory);
//        when(factoryManager.getFactoryImages(FACTORY_ID, null)).thenReturn(new HashSet<>(Collections.singletonList(image)));
//        // when, then
//        given().expect()
//               .statusCode(200)
//               .contentType(MediaType.TEXT_PLAIN)
//               .body(
//                       equalTo("[![alt](" + getServerUrl(context) + "/api/factory/" + FACTORY_ID + "/image?imgId=" +
//                               imageName + ")](" +
//                               getServerUrl(context) + "/factory?id=" +
//                               FACTORY_ID + ")")
//                    )
//               .when()
//               .get(SERVICE_PATH + "/" + FACTORY_ID + "/snippet?type=markdown");
//    }
//
//    @Test
//    public void shouldBeAbleToReturnMarkdownSnippetForFactory1WithoutImage(ITestContext context) throws Exception {
//        // given
//        FactoryDto factory = createFactoryWithStorage1("git", "http://github.com/codenvy/platform-api.git")
//                .withId(FACTORY_ID)
//                .withButton(DTO.createDto(ButtonDto.class)
//                               .withType(ButtonDto.ButtonType.nologo)
//                               .withAttributes(DTO.createDto(ButtonAttributesDto.class)
//                                                  .withColor("white")));
//
//        when(factoryManager.getById(FACTORY_ID)).thenReturn(factory);
//        // when, then
//        given().expect()
//               .statusCode(200)
//               .contentType(MediaType.TEXT_PLAIN)
//               .body(
//                       equalTo("[![alt](" + getServerUrl(context) + "/factory/resources/factory-white.png)](" + getServerUrl
//                               (context) +
//                               "/factory?id=" +
//                               FACTORY_ID + ")")
//                    )
//               .when()
//               .get(SERVICE_PATH + "/" + FACTORY_ID + "/snippet?type=markdown");
//    }
//
//    @Test
//    public void shouldNotBeAbleToGetMarkdownSnippetForFactory1WithoutStyle() throws Exception {
//        // given
//        FactoryDto factory = createFactoryWithStorage1("git", "http://github.com/codenvy/platform-api.git").withId(FACTORY_ID);
//        when(factoryManager.getById(FACTORY_ID)).thenReturn(factory);
//        // when, then
//        Response response = given().expect()
//                                   .statusCode(400)
//                                   .when()
//                                   .get(SERVICE_PATH + "/" + FACTORY_ID + "/snippet?type=markdown");
//
//        assertEquals(DTO.createDtoFromJson(response.getBody().asInputStream(), ServiceError.class).getMessage(),
//                     "Unable to generate markdown snippet for factory without button");
//    }
//
//    @Test
//    public void shouldNotBeAbleToGetMarkdownSnippetForFactory2WithoutColor() throws Exception {
//        // given
//        FactoryDto factory = createFactoryWithStorage1("git", "http://github.com/codenvy/platform-api.git")
//                .withId(FACTORY_ID)
//                .withButton(DTO.createDto(ButtonDto.class)
//                               .withType(ButtonDto.ButtonType.nologo)
//                               .withAttributes(DTO.createDto(ButtonAttributesDto.class)
//                                                  .withColor(null)));
//
//        when(factoryManager.getById(FACTORY_ID)).thenReturn(factory);
//        // when, then
//        Response response = given().expect()
//                                   .statusCode(400)
//                                   .when()
//                                   .get(SERVICE_PATH + "/" + FACTORY_ID + "/snippet?type=markdown");
//
//        assertEquals(DTO.createDtoFromJson(response.getBody().asInputStream(), ServiceError.class).getMessage(),
//                     "Unable to generate markdown snippet with nologo button and empty color");
//    }
//
//    @Test
//    public void shouldResponse404OnGetSnippetIfFactoryDoesNotExist() throws Exception {
//        // given
//        doThrow(new NotFoundException("Factory URL with id " + ILLEGAL_FACTORY_ID + " is not found.")).when(factoryManager)
//                                                                                                      .getById(anyString());
//
//        // when, then
//        Response response = given().expect()
//                                   .statusCode(404)
//                                   .when()
//                                   .get(SERVICE_PATH + "/" + ILLEGAL_FACTORY_ID + "/snippet?type=url");
//
//        assertEquals(DTO.createDtoFromJson(response.getBody().asString(), ServiceError.class).getMessage(),
//                     "Factory URL with id " + ILLEGAL_FACTORY_ID + " is not found.");
//    }


    // FactoryService#getFactoryJson(String wsId, String path) tests:


    // FactoryService#resolveFactory(Map<String, String> parameters, Boolean validate) tests:


//    @Test
//    public void shouldBeAbleToSaveFactoryWithOutImage(ITestContext context) throws Exception {
//        // given
//        FactoryDto factory = createFactoryWithStorage1("git", "http://github.com/codenvy/platform-api.git");
//
//        Link expectedCreateProject =
//                DTO.createDto(Link.class).withMethod(HttpMethod.GET).withProduces("text/html").withRel("accept")
//                   .withHref(getServerUrl(context) + "/f?id=" + FACTORY_ID);
//
//        FactorySaveAnswer factorySaveAnswer = new FactorySaveAnswer();
//        when(factoryManager.saveFactory(any(FactoryDto.class), anySetOf(FactoryImage.class))).then(factorySaveAnswer);
//        when(factoryManager.getById(FACTORY_ID)).then(factorySaveAnswer);
//
//        // when, then
//        Response response =
//                given().auth().basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)//
//                        .multiPart("factory", JsonHelper.toJson(factory), APPLICATION_JSON).when()
//                        .post("/private" + SERVICE_PATH);
//
//        // then
//        assertEquals(response.getStatusCode(), 200);
//        FactoryDto responseFactory = DTO.createDtoFromJson(response.getBody().asString(), FactoryDto.class);
//        assertTrue(responseFactory.getLinks().contains(
//                DTO.createDto(Link.class).withMethod(HttpMethod.GET).withProduces(APPLICATION_JSON)
//                   .withHref(getServerUrl(context) + "/rest/private/factory/" +
//                             FACTORY_ID).withRel("self")
//        ));
//        assertTrue(responseFactory.getLinks().contains(expectedCreateProject));
//        assertTrue(responseFactory.getLinks()
//                                  .contains(DTO.createDto(Link.class).withMethod(HttpMethod.GET).withProduces(MediaType.TEXT_PLAIN)
//                                               .withHref(getServerUrl(context) +
//                                                         "/rest/private/analytics/public-metric/factory_used?factory=" +
//                                                         encode(expectedCreateProject.getHref(), "UTF-8"))
//                                               .withRel("accepted")));
//        assertTrue(responseFactory.getLinks()
//                                  .contains(DTO.createDto(Link.class).withMethod(HttpMethod.GET).withProduces(MediaType.TEXT_PLAIN)
//                                               .withHref(getServerUrl(context) + "/rest/private/factory/" +
//                                                         FACTORY_ID + "/snippet?type=url")
//                                               .withRel("snippet/url")));
//        assertTrue(responseFactory.getLinks()
//                                  .contains(DTO.createDto(Link.class).withMethod(HttpMethod.GET).withProduces(MediaType.TEXT_PLAIN)
//                                               .withHref(getServerUrl(context) + "/rest/private/factory/" +
//                                                         FACTORY_ID + "/snippet?type=html")
//                                               .withRel("snippet/html")));
//        assertTrue(responseFactory.getLinks()
//                                  .contains(DTO.createDto(Link.class).withMethod(HttpMethod.GET).withProduces(MediaType.TEXT_PLAIN)
//                                               .withHref(getServerUrl(context) + "/rest/private/factory/" +
//                                                         FACTORY_ID + "/snippet?type=markdown")
//                                               .withRel("snippet/markdown")));
//
//
//        List<Link> expectedLinks = new ArrayList<>(8);
//        expectedLinks.add(expectedCreateProject);
//
//        Link self = DTO.createDto(Link.class);
//        self.setMethod(HttpMethod.GET);
//        self.setProduces(APPLICATION_JSON);
//        self.setHref(getServerUrl(context) + "/rest/private/factory/" + FACTORY_ID);
//        self.setRel("self");
//        expectedLinks.add(self);
//
//        Link accepted = DTO.createDto(Link.class);
//        accepted.setMethod(HttpMethod.GET);
//        accepted.setProduces(MediaType.TEXT_PLAIN);
//        accepted.setHref(getServerUrl(context) + "/rest/private/analytics/public-metric/factory_used?factory=" +
//                         encode(expectedCreateProject.getHref(), "UTF-8"));
//        accepted.setRel("accepted");
//        expectedLinks.add(accepted);
//
//        Link snippetUrl = DTO.createDto(Link.class);
//        snippetUrl.setProduces(MediaType.TEXT_PLAIN);
//        snippetUrl.setHref(getServerUrl(context) + "/rest/private/factory/" + FACTORY_ID + "/snippet?type=url");
//        snippetUrl.setRel("snippet/url");
//        snippetUrl.setMethod(HttpMethod.GET);
//        expectedLinks.add(snippetUrl);
//
//        Link snippetHtml = DTO.createDto(Link.class);
//        snippetHtml.setProduces(MediaType.TEXT_PLAIN);
//        snippetHtml.setHref(getServerUrl(context) + "/rest/private/factory/" + FACTORY_ID +
//                            "/snippet?type=html");
//        snippetHtml.setMethod(HttpMethod.GET);
//        snippetHtml.setRel("snippet/html");
//        expectedLinks.add(snippetHtml);
//
//        Link snippetMarkdown = DTO.createDto(Link.class);
//        snippetMarkdown.setProduces(MediaType.TEXT_PLAIN);
//        snippetMarkdown.setHref(getServerUrl(context) + "/rest/private/factory/" + FACTORY_ID +
//                                "/snippet?type=markdown");
//        snippetMarkdown.setRel("snippet/markdown");
//        snippetMarkdown.setMethod(HttpMethod.GET);
//        expectedLinks.add(snippetMarkdown);
//
//        Link snippetiFrame = DTO.createDto(Link.class);
//        snippetiFrame.setProduces(MediaType.TEXT_PLAIN);
//        snippetiFrame.setHref(getServerUrl(context) + "/rest/private/factory/" + FACTORY_ID +
//                              "/snippet?type=iframe");
//        snippetiFrame.setRel("snippet/iframe");
//        snippetiFrame.setMethod(HttpMethod.GET);
//        expectedLinks.add(snippetiFrame);
//
//        for (Link link : responseFactory.getLinks()) {
//            //This transposition need because proxy objects doesn't contains equals method.
//            Link testLink = DTO.createDto(Link.class);
//            testLink.setProduces(link.getProduces());
//            testLink.setHref(link.getHref());
//            testLink.setRel(link.getRel());
//            testLink.setMethod(HttpMethod.GET);
//            assertTrue(expectedLinks.contains(testLink));
//        }
//
//        verify(factoryManager).saveFactory(Matchers.<FactoryDto>any(), eq(Collections.<FactoryImage>emptySet()));
//    }


    // TODO: consider how to correctly create factory images
//    @Test
//    public void shouldReturnStatus409OnSaveFactoryIfImageHasUnsupportedMediaType() throws Exception {
//        final Factory factory = createFactory();
//        when(factoryManager.saveFactory(any(FactoryDto.class), anySetOf(FactoryImage.class))).thenReturn(factory);
//        when(factoryManager.getById(FACTORY_ID)).thenReturn(factory);
//
//        final Response response = given().auth()
//                                         .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
//                                         .multiPart("factory", JsonHelper.toJson(factory), APPLICATION_JSON)
//                                         .multiPart("image", getFactoryImage(), "image/tiff")
//                                         .expect()
//                                         .statusCode(409)
//                                         .when().post("/private" + SERVICE_PATH);
//
//        assertEquals(getFromResponse(response, ServiceError.class).getMessage(),
//                     "Image media type 'image/tiff' is unsupported.");
//    }

//
//    @Test
//    public void shouldBeAbleToGetFactory(ITestContext context) throws Exception {
//        // given
//        String factoryName = "factoryName";
//        FactoryDto factory = DTO.createDto(FactoryDto.class);
//        factory.setId(FACTORY_ID);
//        factory.setName(factoryName);
//        factory.setCreator(DTO.createDto(AuthorDto.class).withUserId(USER_ID));
//        URL resource = currentThread().getContextClassLoader().getResource("100x100_image.jpeg");
//        assertNotNull(resource);
//        Path path = Paths.get(resource.toURI());
//        byte[] data = Files.readAllBytes(path);
//        FactoryImage image1 = new FactoryImage(data, "image/jpeg", "image123456789");
//        FactoryImage image2 = new FactoryImage(data, "image/png", "image987654321");
//        Set<FactoryImage> images = new HashSet<>();
//        images.add(image1);
//        images.add(image2);
//        Link expectedCreateProject = DTO.createDto(Link.class);
//        expectedCreateProject.setProduces("text/html");
//        expectedCreateProject.setHref(getServerUrl(context) + "/f?id=" + FACTORY_ID);
//        expectedCreateProject.setRel("accept");
//
//        when(factoryManager.getById(FACTORY_ID)).thenReturn(factory);
//        when(factoryManager.getFactoryImages(FACTORY_ID, null)).thenReturn(images);
//
//        // when
//        Response response = given().when().get(SERVICE_PATH + "/" + FACTORY_ID);
//
//        // then
//        assertEquals(response.getStatusCode(), 200);
//        FactoryDto responseFactory = JsonHelper.fromJson(response.getBody().asString(),
//                                                      FactoryDto.class, null);
//
//        List<Link> expectedLinks = new ArrayList<>(10);
//        expectedLinks.add(expectedCreateProject);
//
//        Link expectedCreateProjectByName = DTO.createDto(Link.class);
//        expectedCreateProjectByName.setProduces("text/html");
//        expectedCreateProjectByName.setHref(getServerUrl(context) + "/f?name=" + factoryName + "&user=" + ADMIN_USER_NAME);
//        expectedCreateProjectByName.setRel("accept-named");
//        expectedLinks.add(expectedCreateProjectByName);
//
//        Link self = DTO.createDto(Link.class);
//        self.setProduces(APPLICATION_JSON);
//        self.setHref(getServerUrl(context) + "/rest/factory/" + FACTORY_ID);
//        self.setRel("self");
//        expectedLinks.add(self);
//
//        Link imageJpeg = DTO.createDto(Link.class);
//        imageJpeg.setProduces("image/jpeg");
//        imageJpeg.setHref(getServerUrl(context) + "/rest/factory/" + FACTORY_ID +
//                          "/image?imgId=image123456789");
//        imageJpeg.setRel("image");
//        expectedLinks.add(imageJpeg);
//
//        Link imagePng = DTO.createDto(Link.class);
//        imagePng.setProduces("image/png");
//        imagePng.setHref(getServerUrl(context) + "/rest/factory/" + FACTORY_ID + "/image?imgId=image987654321");
//        imagePng.setRel("image");
//        expectedLinks.add(imagePng);
//
//        Link accepted = DTO.createDto(Link.class);
//        accepted.setProduces(MediaType.TEXT_PLAIN);
//        accepted.setHref(getServerUrl(context) + "/rest/analytics/public-metric/factory_used?factory=" +
//                         encode(expectedCreateProject.getHref(), "UTF-8"));
//        accepted.setRel("accepted");
//        expectedLinks.add(accepted);
//
//        Link snippetUrl = DTO.createDto(Link.class);
//        snippetUrl.setProduces(MediaType.TEXT_PLAIN);
//        snippetUrl.setHref(getServerUrl(context) + "/rest/factory/" + FACTORY_ID + "/snippet?type=url");
//        snippetUrl.setRel("snippet/url");
//        expectedLinks.add(snippetUrl);
//
//        Link snippetHtml = DTO.createDto(Link.class);
//        snippetHtml.setProduces(MediaType.TEXT_PLAIN);
//        snippetHtml.setHref(getServerUrl(context) + "/rest/factory/" + FACTORY_ID + "/snippet?type=html");
//        snippetHtml.setRel("snippet/html");
//        expectedLinks.add(snippetHtml);
//
//        Link snippetMarkdown = DTO.createDto(Link.class);
//        snippetMarkdown.setProduces(MediaType.TEXT_PLAIN);
//        snippetMarkdown.setHref(getServerUrl(context) + "/rest/factory/" + FACTORY_ID +
//                                "/snippet?type=markdown");
//        snippetMarkdown.setRel("snippet/markdown");
//        expectedLinks.add(snippetMarkdown);
//
//        Link snippetiFrame = DTO.createDto(Link.class);
//        snippetiFrame.setProduces(MediaType.TEXT_PLAIN);
//        snippetiFrame.setHref(getServerUrl(context) + "/rest/factory/" + FACTORY_ID +
//                              "/snippet?type=iframe");
//        snippetiFrame.setRel("snippet/iframe");
//        expectedLinks.add(snippetiFrame);
//
//        for (Link link : responseFactory.getLinks()) {
//            Link testLink = DTO.createDto(Link.class);
//            testLink.setProduces(link.getProduces());
//            testLink.setHref(link.getHref());
//            testLink.setRel(link.getRel());
//            //This transposition need because proxy objects doesn't contains equals method.
//            assertTrue(expectedLinks.contains(testLink));
//        }
//    }
//
//
//    @Test(dataProvider = "badSnippetTypeProvider")
//    public void shouldResponse409OnGetSnippetIfTypeIsIllegal(String type) throws Exception {
//        // given
//        when(factoryManager.getById(FACTORY_ID)).thenReturn(DTO.createDto(FactoryDto.class));
//
//        // when, then
//        Response response = given().expect()
//                                   .statusCode(BAD_REQUEST.getStatusCode())
//                                   .when()
//                                   .get(SERVICE_PATH + "/" + FACTORY_ID + "/snippet?type=" + type);
//
//        assertEquals(DTO.createDtoFromJson(response.getBody().asString(), ServiceError.class).getMessage(),
//                     format("Snippet type \"%s\" is unsupported.", type));
//    }
//
//    @DataProvider(name = "badSnippetTypeProvider")
//    public Object[][] badSnippetTypeProvider() {
//        return new String[][]{{""},
//                              {null},
//                              {"mark"}};
//    }
//
//    private String getServerUrl(ITestContext context) {
//        String serverPort = String.valueOf(context.getAttribute(EverrestJetty.JETTY_PORT));
//        return "http://localhost:" + serverPort;
//    }
//
//
//    @Test
//    public void shouldNotFindWhenNoAttributesProvided() throws Exception {
//        // when
//        Response response = given().auth()
//                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
//                                   .when()
//                                   .get("/private" + SERVICE_PATH + "/find");
//        // then
//        assertEquals(response.getStatusCode(), BAD_REQUEST.getStatusCode());
//    }
//
//    @Test
//    public void shouldFindByAttribute() throws Exception {
//        // given
//        FactoryDto factory = createFactoryWithStorage1("git", "http://github.com/codenvy/platform-api.git")
//                .withId(FACTORY_ID)
//                .withCreator(DTO.createDto(AuthorDto.class).withUserId("uid-123"));
//
//        List<Pair<String, String>> expected = Collections.singletonList(Pair.of("creator.userid", "uid-123"));
//        doReturn(ImmutableList.of(factory, factory)).when(factoryManager).getByAttribute(anyInt(), anyInt(), eq(expected));
//
//        // when
//        Response response = given().auth()
//                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
//                                   .when()
//                                   .get("/private" + SERVICE_PATH + "/find?creator.userid=uid-123");
//
//        // then
//        assertEquals(response.getStatusCode(), 200);
//        List<FactoryDto> responseFactories = DTO.createListDtoFromJson(response.getBody().asString(), FactoryDto.class);
//        assertEquals(responseFactories.size(), 2);
//    }
//
//
//    @Test
//    public void shouldGenerateFactoryJsonIncludeAllProjects() throws Exception {
//        // given
//        final String wsId = "workspace123234";
//        WorkspaceImpl.WorkspaceImplBuilder userWs = WorkspaceImpl.builder();
//        WorkspaceConfigImpl.WorkspaceConfigImplBuilder wsConfig = WorkspaceConfigImpl.builder();
//
//        wsConfig.setProjects(Arrays.asList(DTO.createDto(ProjectConfigDto.class)
//                                              .withSource(DTO.createDto(SourceStorageDto.class)
//                                                             .withType("git")
//                                                             .withLocation("location"))
//                                              .withPath("path"),
//                                           DTO.createDto(ProjectConfigDto.class)
//                                              .withSource(DTO.createDto(SourceStorageDto.class)
//                                                             .withType("git")
//                                                             .withLocation("location"))
//                                              .withPath("path")));
//        wsConfig.setName("wsname");
//        wsConfig.setDefaultEnv("env1");
//        wsConfig.setEnvironments(Collections.singletonList(DTO.createDto(EnvironmentDto.class).withName("env1")));
//        wsConfig.setCommands(Collections.singletonList(DTO.createDto(CommandDto.class)
//                                                          .withName("MCI")
//                                                          .withType("mvn")
//                                                          .withCommandLine("clean install")));
//        userWs.setId(wsId);
//        userWs.setNamespace("id-2314");
//        userWs.setStatus(WorkspaceStatus.RUNNING);
//        userWs.setConfig(wsConfig.build());
//
//        WorkspaceImpl usersWorkspace = userWs.build();
//        when(workspaceManager.getWorkspace(eq(wsId))).thenReturn(usersWorkspace);
//
//        // when
//        Response response = given().auth()
//                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
//                                   .when()
//                                   .get("/private" + SERVICE_PATH + "/workspace/" + wsId);
//
//        // then
//        assertEquals(response.getStatusCode(), 200);
//        FactoryDto result = DTO.createDtoFromJson(response.getBody().asString(), FactoryDto.class);
//        assertEquals(result.getWorkspace().getProjects().size(), 2);
//        assertEquals(result.getWorkspace().getName(), usersWorkspace.getConfig().getName());
//        assertEquals(result.getWorkspace().getEnvironments().get(0).toString(),
//                     asDto(usersWorkspace.getConfig().getEnvironments().get(0)).toString());
//        assertEquals(result.getWorkspace().getCommands().get(0), asDto(usersWorkspace.getConfig().getCommands().get(0)));
//    }
//
//    @Test
//    public void shouldGenerateFactoryJsonIncludeGivenProjects() throws Exception {
//        // given
//        final String wsId = "workspace123234";
//        WorkspaceImpl.WorkspaceImplBuilder ws = WorkspaceImpl.builder();
//        WorkspaceConfigImpl.WorkspaceConfigImplBuilder wsConfig = WorkspaceConfigImpl.builder();
//        ws.setId(wsId);
//        wsConfig.setProjects(Arrays.asList(DTO.createDto(ProjectConfigDto.class)
//                                              .withPath("/proj1")
//                                              .withSource(DTO.createDto(SourceStorageDto.class)
//                                                             .withType("git")
//                                                             .withLocation("location")),
//                                           DTO.createDto(ProjectConfigDto.class)
//                                              .withPath("/proj2")
//                                              .withSource(DTO.createDto(SourceStorageDto.class)
//                                                             .withType("git")
//                                                             .withLocation("location"))));
//        wsConfig.setName("wsname");
//        ws.setNamespace("id-2314");
//        wsConfig.setEnvironments(Collections.singletonList(DTO.createDto(EnvironmentDto.class).withName("env1")));
//        wsConfig.setDefaultEnv("env1");
//        ws.setStatus(WorkspaceStatus.RUNNING);
//        wsConfig.setCommands(Collections.singletonList(DTO.createDto(CommandDto.class)
//                                                          .withName("MCI")
//                                                          .withType("mvn")
//                                                          .withCommandLine("clean install")));
//        ws.setConfig(wsConfig.build());
//        WorkspaceImpl usersWorkspace = ws.build();
//        when(workspaceManager.getWorkspace(eq(wsId))).thenReturn(usersWorkspace);
//
//        // when
//        Response response = given().auth()
//                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
//                                   .when()
//                                   .get("/private" + SERVICE_PATH + "/workspace/" + wsId);
//
//        // then
//        assertEquals(response.getStatusCode(), 200);
//        FactoryDto result = DTO.createDtoFromJson(response.getBody().asString(), FactoryDto.class);
//        assertEquals(result.getWorkspace().getProjects().size(), 2);
//    }
//
//    @Test
//    public void shouldThrowServerExceptionDuringSaveFactory() throws Exception {
//        // given
//        FactoryDto factory = createFactoryWithStorage1("git", "http://github.com/codenvy/che-core.git");
//        URL resource = currentThread().getContextClassLoader().getResource("100x100_image.jpeg");
//        assertNotNull(resource);
//        Path path = Paths.get(resource.toURI());
//        doThrow(IOException.class).when(factoryManager).saveFactory(any(FactoryDto.class), anySetOf(FactoryImage.class));
//
//        // when, then
//        Response response = given().auth()
//                               .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
//                               .multiPart("factory", JsonHelper.toJson(factory), APPLICATION_JSON)
//                               .multiPart("image", path.toFile(), "image/jpeg")
//                               .when()
//                               .post("/private" + SERVICE_PATH);
//        assertEquals(response.getStatusCode(), Status.INTERNAL_SERVER_ERROR.getStatusCode());
//    }
//
//
//    @Test
//    public void shouldThrowBadRequestExceptionWhenTriedToStoreInvalidFactory() throws Exception {
//        // given
//        FactoryDto factory = createFactoryWithStorage1("git", "http://github.com/codenvy/che-core.git");
//        factory.withCreator(DTO.createDto(AuthorDto.class).withName("username"));
//
//        // when, then
//        Response response = given().auth()
//                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
//                                   .contentType(ContentType.JSON)
//                                   .body("")
//                                   .post("/private" + SERVICE_PATH);
//        assertEquals(response.getStatusCode(), BAD_REQUEST.getStatusCode());
//    }
//
//    @Test
//    public void shouldThrowServerExceptionWhenImpossibleCreateLinksForSavedFactory() throws Exception {
//        // given
//        FactoryDto factory = createFactoryWithStorage1("git", "http://github.com/codenvy/che-core.git");
//        factory.withCreator(DTO.createDto(AuthorDto.class).withName("username"));
//        doThrow(UnsupportedEncodingException.class).when(factoryManager).getById(anyString());
//
//        // when, then
//        Response response = given().auth()
//                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
//                                   .contentType(ContentType.JSON)
//                                   .body("{}")
//                                   .post("/private" + SERVICE_PATH);
//        assertEquals(response.getStatusCode(), Status.INTERNAL_SERVER_ERROR.getStatusCode());
//    }
//
//    @Test
//    public void shouldThrowExceptionDuringGetFactory() throws Exception {
//        // given
//        doThrow(UnsupportedEncodingException.class).when(factoryManager).getFactoryImages(anyString(), anyString());
//
//        // when, then
//        Response response = given().auth()
//                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
//                                   .get("/private" + SERVICE_PATH + "/factoryId");
//        assertEquals(response.getStatusCode(), Status.INTERNAL_SERVER_ERROR.getStatusCode());
//    }
//
//    @Test
//    public void shouldGetFactoryAndValidateItOnAccept() throws Exception {
//        // given
//        FactoryDto factory = DTO.createDto(FactoryDto.class)
//                             .withCreator(DTO.createDto(AuthorDto.class)
//                                             .withName(ADMIN_USER_NAME)
//                                             .withUserId(USER_ID))
//                             .withId(FACTORY_ID);
//        when(factoryManager.getById(FACTORY_ID)).thenReturn(factory);
//        doNothing().when(acceptValidator).validateOnAccept(factory);
//
//        // when, then
//        Response response = given().auth()
//                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
//                                   .get("/private" + SERVICE_PATH + '/' + FACTORY_ID + "?validate=true");
//        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
//    }
//
//    @Test
//    public void should() throws Exception {
//        // given
//        FactoryDto factory = DTO.createDto(FactoryDto.class)
//                             .withCreator(DTO.createDto(AuthorDto.class)
//                                             .withName(ADMIN_USER_NAME)
//                                             .withUserId(USER_ID))
//                             .withId(FACTORY_ID);
//        factory.setId(FACTORY_ID);
//
//        FactoryDto storedFactory = DTO.createDto(FactoryDto.class)
//                                   .withId(FACTORY_ID)
//                                   .withCreator(DTO.createDto(AuthorDto.class).withCreated(10L));
//        when(factoryManager.getById(anyString())).thenReturn(storedFactory);
//        doThrow(UnsupportedEncodingException.class).when(factoryManager).getFactoryImages(anyString(), anyString());
//
//        // when, then
//        Response response = given().auth()
//                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
//                                   .contentType(APPLICATION_JSON)
//                                   .body(JsonHelper.toJson(factory))
//                                   .when()
//                                   .put("/private" + SERVICE_PATH + "/" + FACTORY_ID);
//
//        assertEquals(response.getStatusCode(), Status.INTERNAL_SERVER_ERROR.getStatusCode());
//
//    }
//
//    @Test
//    public void shouldNotGenerateFactoryIfNoProjectsWithSourceStorage() throws Exception {
//        // given
//        final String wsId = "workspace123234";
//        WorkspaceImpl.WorkspaceImplBuilder ws = WorkspaceImpl.builder();
//        WorkspaceConfigImpl.WorkspaceConfigImplBuilder wsConfig = WorkspaceConfigImpl.builder();
//        ws.setId(wsId);
//        wsConfig.setProjects(Arrays.asList(DTO.createDto(ProjectConfigDto.class)
//                                              .withPath("/proj1"),
//                                           DTO.createDto(ProjectConfigDto.class)
//                                              .withPath("/proj2")));
//        wsConfig.setName("wsname");
//        ws.setNamespace("id-2314");
//        wsConfig.setEnvironments(Collections.singletonList(DTO.createDto(EnvironmentDto.class).withName("env1")));
//        wsConfig.setDefaultEnv("env1");
//        ws.setStatus(WorkspaceStatus.RUNNING);
//        wsConfig.setCommands(Collections.singletonList(
//                DTO.createDto(CommandDto.class).withName("MCI").withType("mvn").withCommandLine("clean install")));
//        ws.setConfig(wsConfig.build());
//
//        WorkspaceImpl usersWorkspace = ws.build();
//        when(workspaceManager.getWorkspace(eq(wsId))).thenReturn(usersWorkspace);
//
//        // when
//        Response response = given().auth()
//                                   .basic(ADMIN_USER_NAME, ADMIN_USER_PASSWORD)
//                                   .when()
//                                   .get("/private" + SERVICE_PATH + "/workspace/" + wsId);
//
//        // then
//        assertEquals(response.getStatusCode(), BAD_REQUEST.getStatusCode());
//    }
//
//
//
//    /**
//     * Check that if no resolver is plugged, we have correct error
//     */
//    @Test
//    public void noResolver() throws Exception {
//        Set<FactoryParametersResolver> resolvers = new HashSet<>();
//        when(factoryParametersResolvers.stream()).thenReturn(resolvers.stream());
//
//        Map<String, String> map = new HashMap<>();
//        // when
//        Response response = given().contentType(ContentType.JSON).when().body(map).post(SERVICE_PATH_RESOLVER);
//
//        // then check we have a not found
//        assertEquals(response.getStatusCode(), NOT_FOUND.getStatusCode());
//        assertTrue(response.getBody().prettyPrint().contains(ERROR_NO_RESOLVER_AVAILABLE));
//    }
//
//
//    /**
//     * Check that if there is a matching resolver, factory is created
//     */
//    @Test
//    public void matchingResolver() throws Exception {
//        Set<FactoryParametersResolver> resolvers = new HashSet<>();
//        when(factoryParametersResolvers.stream()).thenReturn(resolvers.stream());
//        FactoryParametersResolver dummyResolver = mock(FactoryParametersResolver.class);
//        resolvers.add(dummyResolver);
//
//        // create factory
//        FactoryDto expectFactory = DTO.createDto(FactoryDto.class).withV("4.0").withName("matchingResolverFactory");
//
//        // accept resolver
//        when(dummyResolver.accept(anyMap())).thenReturn(TRUE);
//        when(dummyResolver.createFactory(anyMap())).thenReturn(expectFactory);
//
//        // when
//        Map<String, String> map = new HashMap<>();
//        Response response = given().contentType(ContentType.JSON).when().body(map).post(SERVICE_PATH_RESOLVER);
//
//        // then check we have a not found
//        assertEquals(response.getStatusCode(), OK.getStatusCode());
//        Factory responseFactory = DTO.createDtoFromJson(response.getBody().asInputStream(), Factory.class);
//        assertNotNull(responseFactory);
//        assertEquals(responseFactory.getName(), expectFactory.getName());
//        assertEquals(responseFactory.getV(), expectFactory.getV());
//
//        // check we call resolvers
//        verify(dummyResolver).accept(anyMap());
//        verify(dummyResolver).createFactory(anyMap());
//    }
//
//
//    /**
//     * Check that if there is no matching resolver, there is error
//     */
//    @Test
//    public void notMatchingResolver() throws Exception {
//        Set<FactoryParametersResolver> resolvers = new HashSet<>();
//        when(factoryParametersResolvers.stream()).thenReturn(resolvers.stream());
//
//        FactoryParametersResolver dummyResolver = mock(FactoryParametersResolver.class);
//        resolvers.add(dummyResolver);
//        FactoryParametersResolver fooResolver = mock(FactoryParametersResolver.class);
//        resolvers.add(fooResolver);
//
//
//        // accept resolver
//        when(dummyResolver.accept(anyMap())).thenReturn(FALSE);
//        when(fooResolver.accept(anyMap())).thenReturn(FALSE);
//
//        // when
//        Map<String, String> map = new HashMap<>();
//        Response response = given().contentType(ContentType.JSON).when().body(map).post(SERVICE_PATH_RESOLVER);
//
//        // then check we have a not found
//        assertEquals(response.getStatusCode(), NOT_FOUND.getStatusCode());
//
//        // check we never call create factories on resolver
//        verify(dummyResolver, never()).createFactory(anyMap());
//        verify(fooResolver, never()).createFactory(anyMap());
//    }
//
//    /**
//     * Check that if there is a matching resolver and other not matching, factory is created
//     */
//    @Test
//    public void onlyOneMatchingResolver() throws Exception {
//        Set<FactoryParametersResolver> resolvers = new HashSet<>();
//        when(factoryParametersResolvers.stream()).thenReturn(resolvers.stream());
//
//        FactoryParametersResolver dummyResolver = mock(FactoryParametersResolver.class);
//        resolvers.add(dummyResolver);
//        FactoryParametersResolver fooResolver = mock(FactoryParametersResolver.class);
//        resolvers.add(fooResolver);
//
//        // create factory
//        FactoryDto expectFactory = DTO.createDto(FactoryDto.class).withV("4.0").withName("matchingResolverFactory");
//
//        // accept resolver
//        when(dummyResolver.accept(anyMap())).thenReturn(TRUE);
//        when(dummyResolver.createFactory(anyMap())).thenReturn(expectFactory);
//        when(fooResolver.accept(anyMap())).thenReturn(FALSE);
//
//        // when
//        Map<String, String> map = new HashMap<>();
//        Response response = given().contentType(ContentType.JSON).when().body(map).post(SERVICE_PATH_RESOLVER);
//
//        // then check we have a not found
//        assertEquals(response.getStatusCode(), OK.getStatusCode());
//        FactoryDto responseFactory = DTO.createDtoFromJson(response.getBody().asInputStream(), FactoryDto.class);
//        assertNotNull(responseFactory);
//        assertEquals(responseFactory.getName(), expectFactory.getName());
//        assertEquals(responseFactory.getV(), expectFactory.getV());
//
//        // check we call resolvers
//        verify(dummyResolver).accept(anyMap());
//        verify(dummyResolver).createFactory(anyMap());
//        // never called this resolver
//        verify(fooResolver, never()).createFactory(anyMap());
//    }
//
//
//    /**
//     * Check that if there is a matching resolver, that factory is valid
//     */
//    @Test
//    public void checkValidateResolver() throws Exception {
//        Set<FactoryParametersResolver> resolvers = new HashSet<>();
//        when(factoryParametersResolvers.stream()).thenReturn(resolvers.stream());
//
//        FactoryParametersResolver dummyResolver = mock(FactoryParametersResolver.class);
//        resolvers.add(dummyResolver);
//
//        // invalid factory
//        String invalidFactoryMessage = "invalid factory";
//        doThrow(new BadRequestException(invalidFactoryMessage)).when(acceptValidator).validateOnAccept(any());
//
//        // create factory
//        FactoryDto expectFactory = DTO.createDto(FactoryDto.class).withV("4.0").withName("matchingResolverFactory");
//
//        // accept resolver
//        when(dummyResolver.accept(anyMap())).thenReturn(TRUE);
//        when(dummyResolver.createFactory(anyMap())).thenReturn(expectFactory);
//
//        // when
//        Map<String, String> map = new HashMap<>();
//        Response response = given().contentType(ContentType.JSON).when().body(map).queryParam(VALIDATE_QUERY_PARAMETER, valueOf(true)).post(
//                SERVICE_PATH_RESOLVER);
//
//        // then check we have a not found
//        assertEquals(response.getStatusCode(), BAD_REQUEST.getStatusCode());
//        assertTrue(response.getBody().prettyPrint().contains(invalidFactoryMessage));
//
//        // check we call resolvers
//        verify(dummyResolver).accept(anyMap());
//        verify(dummyResolver).createFactory(anyMap());
//
//        // check we call validator
//        verify(acceptValidator).validateOnAccept(any());
//
//    }


    private Factory createFactory() {
        return createFactoryWithStorage(PROJECT_SOURCE_TYPE, PROJECT_SOURCE_LOCATION);
    }

    private Factory createFactoryWithStorage(String type, String location) {
        return FactoryImpl.builder()
                          .setId(FACTORY_ID)
                          .setVersion("4.0")
                          .setWorkspace(createWorkspaceConfig(type, location))
                          .setCreator(new AuthorImpl(12L, USER_ID))
                          .build();
    }

    private static WorkspaceConfig createWorkspaceConfig(String type, String location) {
        return WorkspaceConfigImpl.builder()
                                  .setName(WORKSPACE_NAME)
                                  .setEnvironments(singletonList(new EnvironmentImpl(createEnvDto())))
                                  .setProjects(createProjects(type, location))
                                  .build();
    }

    private static EnvironmentDto createEnvDto() {
        final MachineConfigImpl devMachine = MachineConfigImpl.builder()
                                                              .setDev(true)
                                                              .setName("dev-machine")
                                                              .setType("docker")
                                                              .setSource(new MachineSourceImpl("location").setLocation("recipe"))
                                                              .setServers(asList(new ServerConfImpl("wsagent",
                                                                                                    "8080",
                                                                                                    "https",
                                                                                                    "path1"),
                                                                                 new ServerConfImpl("ref2",
                                                                                                    "8081",
                                                                                                    "https",
                                                                                                    "path2")))
                                                              .setEnvVariables(singletonMap("key1", "value1"))
                                                              .build();
        return org.eclipse.che.api.workspace.server.DtoConverter.asDto(new EnvironmentImpl("dev-env", null, singletonList(devMachine)));
    }

    private static List<ProjectConfig> createProjects(String type, String location) {
        final ProjectConfigImpl projectConfig = new ProjectConfigImpl();
        projectConfig.setSource(new SourceStorageImpl(type, location, null));
        return ImmutableList.of(projectConfig);
    }

    private static <T> T getFromResponse(Response response, Class<T> clazz) throws Exception {
        return DTO.createDtoFromJson(response.getBody().asInputStream(), clazz);
    }

    private static Path getImagePath() throws Exception {
        final URL res = currentThread().getContextClassLoader().getResource("100x100_image.jpeg");
        assertNotNull(res);
        return Paths.get(res.toURI());
    }
}
