import {FactoryProvider} from "@angular/core";
import * as angular from "angular";

import {DomainTypesService} from "./DomainTypesService";
import {DefaultFeedPropertyService} from "./DefaultFeedPropertyService";
import {RegisterTemplatePropertyService} from "./RegisterTemplatePropertyService";
import {FeedInputProcessorPropertiesTemplateService} from "./FeedInputProcessorPropertiesTemplateService";
import {FeedDetailsProcessorRenderingHelper} from "./FeedDetailsProcessorRenderingHelper";
import { EntityAccessControlDialogService } from "../shared/entity-access-control/EntityAccessControlDialogService";

export class AngularServiceUpgrader {
    constructor(){

    }

    static upgrade(service:Function,name:string = service.name) :FactoryProvider{
        return {
            provide: service,
            useFactory: (i: angular.auto.IInjectorService) => i.get(name),
            deps: ["$injector"]
        }
    }
}

export const entityAccessControlDialogServiceProvider: FactoryProvider = AngularServiceUpgrader.upgrade(EntityAccessControlDialogService);

export const domainTypesServiceProvider: FactoryProvider = AngularServiceUpgrader.upgrade(DomainTypesService);

export const feedPropertyServiceProvider: FactoryProvider = AngularServiceUpgrader.upgrade(DefaultFeedPropertyService,"FeedPropertyService");

export const registerTemplatePropertyServiceProvider: FactoryProvider = AngularServiceUpgrader.upgrade(RegisterTemplatePropertyService);

export const feedInputProcessorPropertiesTemplateServiceProvider: FactoryProvider = AngularServiceUpgrader.upgrade(FeedInputProcessorPropertiesTemplateService);

export const feedDetailsProcessorRenderingHelperProvider: FactoryProvider = AngularServiceUpgrader.upgrade(FeedDetailsProcessorRenderingHelper);