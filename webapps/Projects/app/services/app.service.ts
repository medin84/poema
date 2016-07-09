import { Injectable } from '@angular/core';
import { Http, Headers } from '@angular/http';
import { Observable } from 'rxjs/Observable';

import { FETCH_USER_PROFILE } from '../reducers/authed.reducer';
import { User } from '../models';
import { parseResponseObjects } from '../utils/utils';

const HEADERS = new Headers({
    'Content-Type': 'application/x-www-form-urlencoded; charset=utf-8',
    'Accept': 'application/json'
});

@Injectable()
export class AppService {

    isLogged: boolean = false;

    constructor(
        private http: Http
    ) { }

    getUserProfile() {
        return this.http.get('p?id=userprofile', { headers: HEADERS }).map(response => {
            let res = parseResponseObjects(response.json().objects);
            let pageSize = 20;
            if (res[0].pagesize) {
                pageSize = res[0].pagesize
            }
            return {
                type: FETCH_USER_PROFILE,
                payload: {
                    userProfile: res.employee,
                    languages: res.language.list[0].localizedName,
                    pageSize: pageSize
                }
            }
        });
    }

    updateUserProfile(user: User) {
        //
    }

    logout() {
        return this.http.delete('/');
    }
}
