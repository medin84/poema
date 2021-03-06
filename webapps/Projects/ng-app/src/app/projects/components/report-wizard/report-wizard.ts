import { Component, Output, EventEmitter } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Observable, throwError } from 'rxjs';
import { catchError, finalize, switchMap, map } from 'rxjs/operators';
import { saveAs } from 'file-saver';

import {
    IAction, AppService, DataService, NotificationService,
    DATE_TIME_FORMAT, mdFormat
} from '@nb/core';

import { DATA_EXPORT_URL } from '../../constants';
import { DashboardService } from '../../services/dashboard.service';

interface ReportModel {
    reportProfile: any;
    outputFormat: string;
    startFrom: string;
    endUntil: string;
}

@Component({
    selector: 'report-wizard',
    templateUrl: './report-wizard.html',
    styleUrls: ['./report-wizard.css']
})
export class ReportWizardComponent {
    DATA_EXPORT_URL = DATA_EXPORT_URL;

    @Output() cancel = new EventEmitter();

    model: ReportModel = {
        reportProfile: null,
        outputFormat: 'PDF',
        startFrom: null,
        endUntil: null
    };
    data: any = {
        exportFormatTypes: []
    };
    errors: any = {};

    constructor(
        private ngxTranslate: TranslateService,
        private notifyService: NotificationService,
        private appService: AppService,
        private dataService: DataService,
        private dashboardService: DashboardService
    ) { }

    get isValid(): boolean {
        return !!this.model.reportProfile;
    }

    ngOnInit() {
        this.dashboardService.fetchData({}).subscribe(response => {
            // this.data = response.data;
            this.data.exportFormatTypes = response.data.exportFormatType.filter(it => it !== 'UNKNOWN').map(it => {
                return {
                    id: it,
                    name: it,
                    icon: '/SharedResources/nb/img/file-extension/' + it.toLowerCase() + '.png'
                };
            });
        });
    }

    generateReport() {
        let noty = this.notifyService.process(this.ngxTranslate.instant('report_generation')).show();
        let url = `${DATA_EXPORT_URL.API_REPORT_PROFILES}/action/toForm`;
        return this.dataService.apiPostDownload(url, {
            id: this.model.reportProfile.id,
            outputFormat: this.model.outputFormat,
            startFrom: mdFormat(this.model.startFrom, DATE_TIME_FORMAT),
            endUntil: mdFormat(this.model.endUntil, DATE_TIME_FORMAT)
        })
            .pipe(map((response: any) => {
                this.cancel.emit();

                let disposition = response.headers.get('content-disposition');
                let filenameRegex = /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/;
                let matches = filenameRegex.exec(disposition);
                let filename: string = '';
                if (matches != null && matches[1]) {
                    if (matches[1].indexOf("'") != -1) {
                        filename = matches[1].split("'")[1].replace(/['"]/g, '');
                    } else {
                        filename = matches[1].replace(/['"]/g, '');
                    }
                }
                let ldi = filename.lastIndexOf('.');
                let fileExt = filename.substring(ldi + 1);
                let blob = new Blob([response.body], { type: 'application/' + fileExt });

                saveAs(blob, filename);
                return response;
            })
                , catchError(err => {
                    let fileAsTextObservable = new Observable<string>(observer => {
                        const reader = new FileReader();
                        reader.onload = (e) => {
                            let responseText = (<any>e.target).result;

                            observer.next(responseText);
                            observer.complete();
                        };
                        const errMsg = reader.readAsText(err.error, 'utf-8');
                    });

                    return fileAsTextObservable
                        .pipe(switchMap(errMsgJsonAsText => {
                            let _err = errMsgJsonAsText ? JSON.parse(errMsgJsonAsText) : { message: ['Error'] };
                            this.handleRequestError(_err, 3000);
                            return throwError(_err);
                        }));
                })
                , finalize(() => noty.remove()))
            .subscribe();
    }

    handleMessages(option: { messages: string[], isError?: boolean, delay?: number }) {
        if (option.messages) {
            option.messages.forEach(msg => {
                if (msg) {
                    if (option.isError) {
                        if (option.delay) {
                            this.notifyService.error(this.ngxTranslate.instant(msg)).show().remove(option.delay);
                        } else {
                            this.notifyService.error(this.ngxTranslate.instant(msg)).show();
                        }
                    } else {
                        this.notifyService.success(this.ngxTranslate.instant(msg)).show().remove(option.delay || 3000);
                    }
                }
            });
        }
    }

    handleRequestError(error: any = {}, delay: number = 0) {
        this.errors = {};

        this.handleMessages({ messages: error.message, isError: true, delay });

        if (error.payload && error.payload.error && error.payload.error.type === 'INCONSISTENT_DATA') {
            for (let err of error.payload.error.errors) {
                this.errors[err.field] = {
                    message: err.message,
                    error: err.error
                };
            }
        }

        return error;
    }
}
