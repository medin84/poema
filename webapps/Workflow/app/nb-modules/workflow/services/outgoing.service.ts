import { Injectable } from '@angular/core';
import { Http, Headers, Response } from '@angular/http';

import { AppService } from '../../../services/app.service';
import { Outgoing } from '../models';
import { createURLSearchParams, parseResponseObjects, serializeObj, transformPostResponse } from '../../../utils/utils';

const HEADERS = new Headers({
    'Content-Type': 'application/x-www-form-urlencoded; charset=utf-8',
    'Accept': 'application/json'
});

@Injectable()
export class WorkflowOutgoingService {

    constructor(
        private http: Http,
        private appService: AppService
    ) { }

    fetchOutgoings(queryParams = {}) {
        return this.http.get('/Workflow/p?id=outgoing-view', {
            headers: HEADERS,
            search: createURLSearchParams(queryParams)
        })
            .map(response => response.json().objects[1])
            .map(data => {
                return {
                    list: <Outgoing[]>data.list,
                    meta: data.meta
                };
            })
            .catch(error => this.appService.handleError(error));
    }

    fetchOutgoingById(id: string) {
        let url = '/Workflow/p?id=outgoing-form&docid=' + (id !== 'new' ? id : '');

        return this.http.get(url, { headers: HEADERS })
            .map(response => {
                let data = parseResponseObjects(response.json().objects);
                let outgoing = <any>data.outgoing;
                if (!outgoing.id) {
                    outgoing.id = '';
                }
                if (data.fsid) {
                    outgoing.fsid = data.fsid;
                }
                if (data.ACL) {
                    outgoing.acl = data.ACL;
                }
                if (data.attachment) {
                    outgoing.attachments = data.attachment.list;
                }
                return {
                    outgoing: <Outgoing>outgoing,
                    actions: data.actions
                }
            })
            .catch(error => this.appService.handleError(error));
    }
}