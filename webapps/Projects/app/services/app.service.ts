import { Injectable, Inject } from '@angular/core';
import { Http, Headers } from '@angular/http';

import { User } from '../models/user';

@Injectable()
export class AppService {

    constructor(
        private http: Http
    ) { }

    getTranslations() {
        let header = { headers: new Headers({ 'Accept': 'application/json' }) };
        let url = 'p?id=common-captions';

        return this.http.get(url, header)
            .map(response => response.json().captions);
    }

    getNav() {
        let header = { headers: new Headers({ 'Accept': 'application/json' }) };
        let url = 'p?id=outline';

        return this.http.get(url, header);
    }

    getUsers() {
        let header = { headers: new Headers({ 'Accept': 'application/json' }) };
        let url = 'p?id=users';

        return this.http.get(url, header)
            .map(response => response.json().objects[0].list)
            .map((response: User[]) => response);
    }

    updateUserProfile(user: User) {
        //
    }

    logout() {
        return this.http.delete('/');
    }
}